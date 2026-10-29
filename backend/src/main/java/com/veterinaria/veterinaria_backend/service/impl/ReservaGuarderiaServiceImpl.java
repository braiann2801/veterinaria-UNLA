package com.veterinaria.veterinaria_backend.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.AforoGuarderiaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.ReservaGuarderiaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ReservaGuarderiaResponseDTO;
import com.veterinaria.veterinaria_backend.entity.ControlAforoDiario;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.ReservaGuarderia;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.repository.MascotaRepository;
import com.veterinaria.veterinaria_backend.repository.ReservaGuarderiaRepository;
import com.veterinaria.veterinaria_backend.service.ComandaCobroService;
import com.veterinaria.veterinaria_backend.service.ReservaGuarderiaService;
import com.veterinaria.veterinaria_backend.service.TarifaGuarderiaService;

/**
 * Implementacion transaccional de {@link ReservaGuarderiaService}.
 *
 * <p>El aforo se valida bajo un lock pesimista de escritura sobre la fila de
 * {@link ControlAforoDiario} del dia ({@code SELECT ... FOR UPDATE}). El
 * conteo y el insert ocurren dentro de la misma transaccion y con el lock
 * tomado, de modo que las reservas concurrentes se serializan: la segunda
 * espera al commit de la primera y recien entonces cuenta. El contador
 * desnormalizado de la fila es informativo; la fuente de verdad es el
 * {@code COUNT} sobre {@code reserva_guarderia}.</p>
 */
@Service
@Transactional(readOnly = true)
public class ReservaGuarderiaServiceImpl implements ReservaGuarderiaService {

	/** Estados que ocupan cupo. Finalizado y cancelado liberan lugar. */
	private static final List<ReservaGuarderia.EstadoReserva> ESTADOS_QUE_OCUPAN = List.of(
			ReservaGuarderia.EstadoReserva.RESERVADO,
			ReservaGuarderia.EstadoReserva.INGRESADO);

	private final ReservaGuarderiaRepository reservaRepository;
	private final MascotaRepository mascotaRepository;
	private final ControlAforoService controlAforoService;
	private final ComandaCobroService comandaService;
	private final TarifaGuarderiaService tarifaService;

	/** Tope de una estadia por rango: evita bloquear cientos de semaforos por un error de fecha. */
	static final int MAXIMO_DIAS_ESTADIA = 60;

	public ReservaGuarderiaServiceImpl(ReservaGuarderiaRepository reservaRepository,
			MascotaRepository mascotaRepository, ControlAforoService controlAforoService,
			ComandaCobroService comandaService, TarifaGuarderiaService tarifaService) {
		this.reservaRepository = reservaRepository;
		this.mascotaRepository = mascotaRepository;
		this.controlAforoService = controlAforoService;
		this.comandaService = comandaService;
		this.tarifaService = tarifaService;
	}

	@Override
	public List<ReservaGuarderiaResponseDTO> listarPorFecha(LocalDate fecha) {
		LocalDate dia = fecha != null ? fecha : LocalDate.now();
		long ocupados = reservaRepository.contarCuposOcupados(dia);

		return reservaRepository.findByFechaOrdenadasPorHorario(dia).stream()
				.map(reserva -> toResponse(reserva, ocupados))
				.toList();
	}

	/**
	 * Reserva un cupo bajo lock pesimista de escritura.
	 *
	 * <p><b>READ_COMMITTED es obligatorio aqui.</b> MySQL/MariaDB corre en
	 * REPEATABLE-READ por defecto, donde un SELECT plano lee el snapshot que se
	 * fijo al inicio de la transaccion. El {@code COUNT} posterior al
	 * {@code SELECT ... FOR UPDATE} veria el estado viejo, el filtro de aforo
	 * se evaluaria contra informacion obsoleta y dos peticiones simultaneas
	 * seguirian pasando las dos. En READ_COMMITTED cada sentencia ve lo ultimo
	 * confirmado, que es justo lo que necesita el patron leer-contar-escribir
	 * protegido por el lock.</p>
	 */
	@Override
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ReservaGuarderiaResponseDTO crear(ReservaGuarderiaRequestDTO request) {
		Mascota mascota = obtenerMascota(request.mascotaId());

		// Se arma y valida la reserva antes de tomar locks: un rango invalido no
		// tiene por que esperar el semaforo de nadie.
		ReservaGuarderia reserva = new ReservaGuarderia();
		reserva.setFecha(request.fecha());
		reserva.setEstado(ReservaGuarderia.EstadoReserva.RESERVADO);
		reserva.setMascota(mascota);
		if (request.fechaSalida() != null) {
			aplicarRango(reserva, request);
		} else {
			reserva.setTipoEstadia(request.tipoEstadia() != null
					? ReservaGuarderia.TipoEstadia.valueOf(request.tipoEstadia().name())
					: ReservaGuarderia.TipoEstadia.COMPLETA_24H);
			aplicarHorario(reserva, request.horaEntrada(), request.horaSalida(), reserva.getTipoEstadia());
		}
		reserva.setSena(request.sena());
		reserva.setObservaciones(request.observaciones());

		// A partir de aqui corre bajo SELECT ... FOR UPDATE sobre el semaforo de
		// CADA dia de la estadia. Toda la seccion check-then-act queda
		// serializada: la peticion B espera al commit de A antes de contar, asi
		// que nunca ve un conteo viejo. Los dias se bloquean en orden ascendente:
		// dos estadias que se pisan toman los locks en el mismo orden y no se
		// pueden trabar mutuamente (deadlock).
		List<LocalDate> dias = reserva.getFecha().datesUntil(reserva.getFechaFin().plusDays(1)).toList();
		List<ControlAforoDiario> semaforos = dias.stream().map(controlAforoService::bloquearDia).toList();

		// El chequeo de mascota duplicada va dentro del lock a proposito: si
		// quedara antes, dos peticiones concurrentes de la MISMA mascota
		// podrian pasar ambas el EXISTS y devolver dos reservas para un perro.
		if (reservaRepository.existeEstadiaSolapada(mascota.getId(), reserva.getFecha(), reserva.getFechaFin(),
				ESTADOS_QUE_OCUPAN)) {
			throw new BadRequestException("La mascota " + mascota.getId()
					+ " ya tiene una reserva activa " + describirDias(reserva));
		}

		List<Long> ocupadosPorDia = new java.util.ArrayList<>();
		for (LocalDate dia : dias) {
			long ocupados = contarOcupados(dia);
			if (ocupados >= ReservaGuarderia.AFORO_MAXIMO) {
				throw BusinessConflictException.aforoLleno(dia, ocupados, ReservaGuarderia.AFORO_MAXIMO);
			}
			ocupadosPorDia.add(ocupados);
		}

		ReservaGuarderia guardada = reservaRepository.save(reserva);

		// La estadia se cobra desde la reserva: asi se puede pagar o señar por
		// adelantado y el egreso exige que este saldada. El monto sale de la misma
		// formula que muestra la pantalla (por horas o por noches). En la misma
		// transaccion que la reserva: si la comanda falla, no queda reserva sin cobro.
		Long comandaId = comandaService.emitirParaReserva(guardada,
				() -> tarifaService.cotizar(inicio(guardada), fin(guardada)).total());

		// Instantanea informativa. La fuente de verdad sigue siendo el COUNT.
		for (int i = 0; i < semaforos.size(); i++) {
			semaforos.get(i).setCuposOcupados((int) (ocupadosPorDia.get(i) + 1));
		}

		return toResponse(guardada, ocupadosPorDia.get(0) + 1, comandaId);
	}

	@Override
	@Transactional
	public ReservaGuarderiaResponseDTO actualizar(Long id, ReservaGuarderiaRequestDTO request) {
		ReservaGuarderia reserva = obtenerReserva(id);

		if (request.tipoEstadia() != null) {
			reserva.setTipoEstadia(
					ReservaGuarderia.TipoEstadia.valueOf(request.tipoEstadia().name()));
		}
		aplicarHorario(reserva, request.horaEntrada(), request.horaSalida(), reserva.getTipoEstadia());
		reserva.setSena(request.sena());
		reserva.setObservaciones(request.observaciones());

		return toResponse(reserva, reservaRepository.contarCuposOcupados(reserva.getFecha()));
	}

	@Override
	@Transactional
	public ReservaGuarderiaResponseDTO cancelar(Long id) {
		ReservaGuarderia reserva = obtenerReserva(id);
		if (reserva.getEstado() == ReservaGuarderia.EstadoReserva.CANCELADO) {
			throw new BadRequestException("La reserva " + id + " ya estaba cancelada");
		}

		reserva.setEstado(ReservaGuarderia.EstadoReserva.CANCELADO);
		comandaService.anularDeReserva(id);
		return toResponse(reserva, reservaRepository.contarCuposOcupados(reserva.getFecha()));
	}

	@Override
	@Transactional
	public void eliminar(Long id) {
		ReservaGuarderia reserva = obtenerReserva(id);
		// Antes que la reserva: la comanda la referencia. Con pagos responde 409.
		comandaService.eliminarDeReserva(id);
		reservaRepository.delete(reserva);
	}

	@Override
	public AforoGuarderiaResponseDTO consultarAforo(LocalDate fecha) {
		LocalDate dia = fecha != null ? fecha : LocalDate.now();
		return construirAforo(dia, reservaRepository.contarCuposOcupados(dia));
	}

	// ------------------------------------------------------------- internos

	/**
	 * Conteo de cupos ocupados de un dia.
	 *
	 * <p>Siempre contra la base, nunca contando la lista en memoria. Dentro de
	 * {@link #crear} se ejecuta con el lock tomado y en READ_COMMITTED, asi que
	 * ve las reservas que las transacciones anteriores ya confirmaron.</p>
	 */
	private long contarOcupados(LocalDate fecha) {
		return reservaRepository.contarCuposOcupados(fecha);
	}

	/**
	 * Valida y asigna el par de horarios de la reserva.
	 *
	 * <p>Las dos horas van juntas o no van: media reserva sin horario no alcanza
	 * para que el mostrador sepa cuando abrir el canil. Si viene solo una, se
	 * rechaza en vez de inventar la complementaria.</p>
	 *
	 * <p><b>La igualdad depende del tipo de estadia</b>, y esta es la parte
	 * delicate del metodo. Las horas son horas de reloj, no instantes, asi que no
	 * se puede responder con un {@code !salida.isAfter(entrada)} a secas:</p>
	 * <ul>
	 *   <li>En {@code COMPLETA_24H} el perro sale a la misma hora a la que
	 *       entro, pero del dia siguiente: 08:00 -&gt; 08:00 es una estadia de
	 *       24 horas, no una de duracion cero. Es el caso legitimo mas comun.</li>
	 *   <li>En {@code DIURNA} o {@code NOCTURNA} la salida cae dentro del mismo
	 *       dia, asi que ah si 09:00 -&gt; 09:00 es un error de tipeo y se
	 *       rechaza.</li>
	 * </ul>
	 *
	 * <p>Por eso el tipo viaja como parametro y no se lee de la entidad: en
	 * {@link #actualizar} el tipo del request puede ser null (el usuario no lo
	 * toco) mientras el horario si se esta actualizando, y hay que validar
	 * contra el tipo <i>efectivo</i>, no contra el que vino en el payload.</p>
	 */
	private void aplicarHorario(ReservaGuarderia reserva, LocalTime entrada, LocalTime salida,
			ReservaGuarderia.TipoEstadia tipoEstadia) {
		if (entrada == null && salida == null) {
			reserva.setHoraEntrada(null);
			reserva.setHoraSalida(null);
			return;
		}
		if (entrada == null || salida == null) {
			throw new BadRequestException(
					"La hora de entrada y la de salida deben informarse juntas");
		}

		boolean estadiaCorta = tipoEstadia == ReservaGuarderia.TipoEstadia.DIURNA
				|| tipoEstadia == ReservaGuarderia.TipoEstadia.NOCTURNA;
		if (estadiaCorta && !salida.isAfter(entrada)) {
			throw new BadRequestException(
					"En una estadia " + tipoEstadia + " la hora de salida (" + salida
							+ ") debe ser posterior a la de entrada (" + entrada + ")");
		}

		reserva.setHoraEntrada(entrada);
		reserva.setHoraSalida(salida);
	}

	/**
	 * Estadia por rango: entrada {@code fecha + horaEntrada}, salida
	 * {@code fechaSalida + horaSalida}. El tipo se deduce: mismo dia es DIURNA,
	 * con al menos una noche es COMPLETA_24H.
	 */
	private void aplicarRango(ReservaGuarderia reserva, ReservaGuarderiaRequestDTO request) {
		if (request.horaEntrada() == null || request.horaSalida() == null) {
			throw new BadRequestException("Una estadia por rango necesita hora de entrada y hora de salida");
		}
		LocalDateTime entrada = request.fecha().atTime(request.horaEntrada());
		LocalDateTime salida = request.fechaSalida().atTime(request.horaSalida());
		if (!salida.isAfter(entrada)) {
			throw new BadRequestException("La salida (" + salida + ") tiene que ser posterior a la entrada ("
					+ entrada + ")");
		}
		if (request.fechaSalida().isAfter(request.fecha().plusDays(MAXIMO_DIAS_ESTADIA))) {
			throw new BadRequestException("Una estadia no puede superar los " + MAXIMO_DIAS_ESTADIA + " dias");
		}
		boolean mismoDia = request.fechaSalida().isEqual(request.fecha());
		reserva.setFechaSalida(mismoDia ? null : request.fechaSalida());
		reserva.setTipoEstadia(mismoDia ? ReservaGuarderia.TipoEstadia.DIURNA : ReservaGuarderia.TipoEstadia.COMPLETA_24H);
		reserva.setHoraEntrada(request.horaEntrada());
		reserva.setHoraSalida(request.horaSalida());
	}

	/**
	 * Momento de entrada para cotizar. Las reservas de un dia sin horario (las
	 * de siempre) entran a las 08:00.
	 */
	static LocalDateTime inicio(ReservaGuarderia reserva) {
		LocalTime hora = reserva.getHoraEntrada() != null ? reserva.getHoraEntrada() : LocalTime.of(8, 0);
		return reserva.getFecha().atTime(hora);
	}

	/**
	 * Momento de salida para cotizar. Con rango, {@code fechaSalida + horaSalida}.
	 * Sin rango vale la regla de siempre: si la hora de salida no es posterior a
	 * la de entrada, sale al dia siguiente (estadia de 24 h). Sin horario, una
	 * DIURNA sale a las 18:00 y el resto al dia siguiente a la misma hora.
	 */
	static LocalDateTime fin(ReservaGuarderia reserva) {
		LocalDateTime entrada = inicio(reserva);
		if (reserva.getHoraSalida() == null) {
			return reserva.getTipoEstadia() == ReservaGuarderia.TipoEstadia.DIURNA
					? reserva.getFecha().atTime(18, 0)
					: entrada.plusDays(1);
		}
		LocalDateTime salida = reserva.getFechaFin().atTime(reserva.getHoraSalida());
		return salida.isAfter(entrada) ? salida : salida.plusDays(1);
	}

	private static String describirDias(ReservaGuarderia reserva) {
		// Mismo texto de siempre para un solo dia: "... para el 2026-11-10".
		return reserva.getFechaSalida() == null
				? "para el " + reserva.getFecha()
				: "que se pisa con el rango " + reserva.getFecha() + " a " + reserva.getFechaSalida();
	}

	private AforoGuarderiaResponseDTO construirAforo(LocalDate fecha, long ocupados) {
		int maximo = ReservaGuarderia.AFORO_MAXIMO;
		int disponibles = (int) Math.max(0, maximo - ocupados);
		return new AforoGuarderiaResponseDTO(fecha, ocupados, maximo, disponibles, disponibles == 0);
	}

	private ReservaGuarderiaResponseDTO toResponse(ReservaGuarderia reserva, long ocupados) {
		return toResponse(reserva, ocupados, comandaService.comandaDeReserva(reserva.getId()));
	}

	private ReservaGuarderiaResponseDTO toResponse(ReservaGuarderia reserva, long ocupados, Long comandaId) {
		Mascota mascota = reserva.getMascota();
		return new ReservaGuarderiaResponseDTO(
				reserva.getId(),
				reserva.getFecha(),
				reserva.getFechaSalida(),
				reserva.getTipoEstadia() != null ? reserva.getTipoEstadia().name() : null,
				reserva.getEstado() != null ? reserva.getEstado().name() : null,
				mascota != null ? mascota.getId() : null,
				mascota != null ? mascota.getNombre() : null,
				reserva.getHoraEntrada(),
				reserva.getHoraSalida(),
				reserva.getSena(),
				reserva.getObservaciones(),
				ocupados,
				ReservaGuarderia.AFORO_MAXIMO,
				comandaId);
	}

	private Mascota obtenerMascota(Long id) {
		if (id == null) {
			throw new BadRequestException("El id de la mascota es obligatorio");
		}
		return mascotaRepository.findById(id)
				.orElseThrow(() -> ResourceNotFoundException.mascota(id));
	}

	private ReservaGuarderia obtenerReserva(Long id) {
		if (id == null) {
			throw new BadRequestException("El id de la reserva es obligatorio");
		}
		return reservaRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No existe una reserva con id " + id));
	}
}