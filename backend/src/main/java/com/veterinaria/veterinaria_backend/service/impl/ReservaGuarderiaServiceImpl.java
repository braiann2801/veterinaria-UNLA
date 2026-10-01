package com.veterinaria.veterinaria_backend.service.impl;

import java.time.LocalDate;
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
import com.veterinaria.veterinaria_backend.service.ReservaGuarderiaService;

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

	public ReservaGuarderiaServiceImpl(ReservaGuarderiaRepository reservaRepository,
			MascotaRepository mascotaRepository, ControlAforoService controlAforoService) {
		this.reservaRepository = reservaRepository;
		this.mascotaRepository = mascotaRepository;
		this.controlAforoService = controlAforoService;
	}

	@Override
	public List<ReservaGuarderiaResponseDTO> listarPorFecha(LocalDate fecha) {
		LocalDate dia = fecha != null ? fecha : LocalDate.now();
		long ocupados = reservaRepository.contarCuposOcupados(dia);

		return reservaRepository.findByFecha(dia).stream()
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

		// A partir de aqui corre bajo SELECT ... FOR UPDATE sobre el semaforo
		// del dia. Toda la seccion check-then-act queda serializada: la peticion
		// B espera al commit de A antes de contar, asi que nunca ve un conteo
		// viejo. Sin este lock, dos peticiones del cupo 10 y 11 pasarian ambas
		// el filtro y el dia quedaria con 11.
		ControlAforoDiario semaforo = controlAforoService.bloquearDia(request.fecha());

		// El chequeo de mascota duplicada va dentro del lock a proposito: si
		// quedara antes, dos peticiones concurrentes de la MISMA mascota
		// podrian pasar ambas el EXISTS y devolver dos reservas para un perro.
		if (reservaRepository.existsByMascotaIdAndFechaAndEstadoIn(
				mascota.getId(), request.fecha(), ESTADOS_QUE_OCUPAN)) {
			throw new BadRequestException(
					"La mascota " + mascota.getId() + " ya tiene una reserva activa para el " + request.fecha());
		}

		long ocupados = contarOcupados(request.fecha());
		if (ocupados >= ReservaGuarderia.AFORO_MAXIMO) {
			throw BusinessConflictException.aforoLleno(request.fecha(), ocupados,
					ReservaGuarderia.AFORO_MAXIMO);
		}

		ReservaGuarderia reserva = new ReservaGuarderia();
		reserva.setFecha(request.fecha());
		reserva.setTipoEstadia(request.tipoEstadia() != null
				? ReservaGuarderia.TipoEstadia.valueOf(request.tipoEstadia().name())
				: ReservaGuarderia.TipoEstadia.COMPLETA_24H);
		reserva.setEstado(ReservaGuarderia.EstadoReserva.RESERVADO);
		reserva.setMascota(mascota);
		reserva.setSena(request.sena());
		reserva.setObservaciones(request.observaciones());

		ReservaGuarderia guardada = reservaRepository.save(reserva);

		// Instantanea informativa. La fuente de verdad sigue siendo el COUNT.
		semaforo.setCuposOcupados((int) (ocupados + 1));

		return toResponse(guardada, ocupados + 1);
	}

	@Override
	@Transactional
	public ReservaGuarderiaResponseDTO actualizar(Long id, ReservaGuarderiaRequestDTO request) {
		ReservaGuarderia reserva = obtenerReserva(id);

		if (request.tipoEstadia() != null) {
			reserva.setTipoEstadia(
					ReservaGuarderia.TipoEstadia.valueOf(request.tipoEstadia().name()));
		}
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
		return toResponse(reserva, reservaRepository.contarCuposOcupados(reserva.getFecha()));
	}

	@Override
	@Transactional
	public void eliminar(Long id) {
		reservaRepository.delete(obtenerReserva(id));
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

	private AforoGuarderiaResponseDTO construirAforo(LocalDate fecha, long ocupados) {
		int maximo = ReservaGuarderia.AFORO_MAXIMO;
		int disponibles = (int) Math.max(0, maximo - ocupados);
		return new AforoGuarderiaResponseDTO(fecha, ocupados, maximo, disponibles, disponibles == 0);
	}

	private ReservaGuarderiaResponseDTO toResponse(ReservaGuarderia reserva, long ocupados) {
		Mascota mascota = reserva.getMascota();
		return new ReservaGuarderiaResponseDTO(
				reserva.getId(),
				reserva.getFecha(),
				reserva.getTipoEstadia() != null ? reserva.getTipoEstadia().name() : null,
				reserva.getEstado() != null ? reserva.getEstado().name() : null,
				mascota != null ? mascota.getId() : null,
				mascota != null ? mascota.getNombre() : null,
				reserva.getSena(),
				reserva.getObservaciones(),
				ocupados,
				ReservaGuarderia.AFORO_MAXIMO);
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