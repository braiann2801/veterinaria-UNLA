package com.veterinaria.veterinaria_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.CierreCajaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.ComandaCobroResponseDTO;
import com.veterinaria.veterinaria_backend.dto.MovimientoCajaDTO;
import com.veterinaria.veterinaria_backend.dto.PagoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.PagoResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TutorDelDiaResponseDTO;
import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.Pago;
import com.veterinaria.veterinaria_backend.entity.Tutor;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.mapper.CobroMapper;
import com.veterinaria.veterinaria_backend.repository.ComandaCobroRepository;
import com.veterinaria.veterinaria_backend.repository.MovimientoCajaProyeccion;
import com.veterinaria.veterinaria_backend.repository.PagoRepository;
import com.veterinaria.veterinaria_backend.repository.TutorMascotaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorRepository;
import com.veterinaria.veterinaria_backend.service.CajaService;
import com.veterinaria.veterinaria_backend.service.JornadaDelDiaService;

/**
 * Implementacion transaccional de {@link CajaService}.
 *
 * <p><b>Todo el cobro entra por aca.</b> El saldo de una comanda solo baja
 * dentro de un {@code registrarPago}, y siempre bajo un
 * {@code SELECT ... FOR UPDATE} sobre la comanda.</p>
 */
@Service
@Transactional(readOnly = true)
public class CajaServiceImpl implements CajaService {

	/** Cero con la misma escala que los montos, para que el mapa no mezcle 0 y 0.00. */
	private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

	private final ComandaCobroRepository comandaRepository;
	private final PagoRepository pagoRepository;
	private final TutorMascotaRepository vinculoRepository;
	private final TutorRepository tutorRepository;
	private final JornadaDelDiaService jornadaDelDiaService;
	private final CobroMapper mapper;

	public CajaServiceImpl(ComandaCobroRepository comandaRepository,
			PagoRepository pagoRepository,
			TutorMascotaRepository vinculoRepository,
			TutorRepository tutorRepository,
			JornadaDelDiaService jornadaDelDiaService,
			CobroMapper mapper) {
		this.comandaRepository = comandaRepository;
		this.pagoRepository = pagoRepository;
		this.vinculoRepository = vinculoRepository;
		this.tutorRepository = tutorRepository;
		this.jornadaDelDiaService = jornadaDelDiaService;
		this.mapper = mapper;
	}

	@Override
	@Transactional
	public ComandaCobroResponseDTO registrarPago(Long comandaId, PagoRequestDTO request) {
		ComandaCobro comanda = bloquearComanda(comandaId);

		if (comanda.getEstado() == ComandaCobro.EstadoComanda.CANCELADA) {
			throw new BusinessConflictException(
					"La comanda " + comandaId + " esta cancelada: no admite pagos.");
		}
		if (comanda.getEstado() == ComandaCobro.EstadoComanda.PAGADA) {
			throw new BusinessConflictException(
					"La comanda " + comandaId + " ya esta saldada.");
		}

		// Se resuelve una sola vez por operacion: es la misma persona en los tres
		// medios de un cobro mixto, y validarlo por linea haria tres viajes a la
		// base y tres mensajes de error posibles por la misma causa.
		Tutor pagador = resolverPagador(comanda, request.tutorId());

		BigDecimal saldoAntes = comanda.getSaldoPendiente();

		// Todos los pagos de la llamada se aplican en esta transaccion. Si el
		// segundo excede el saldo, la excepcion revierte el primero: el cliente
		// no puede quedar con medio cobro registrado.
		for (PagoRequestDTO.DetallePagoDTO detalle : request.pagos()) {
			aplicarPago(comanda, detalle, saldoAntes, pagador);
			saldoAntes = comanda.getSaldoPendiente();
		}

		// Flush antes de proyectar: los pagos nuevos todavia no tienen id
		// asignado porque la cascada los persiste recien en el flush. Sin esto
		// la respuesta devolveria cada pago con "id": null y el mostrador no
		// tendria forma de referenciarlo despues.
		comandaRepository.flush();
		return mapper.toResponse(comanda);
	}

	@Override
	public List<PagoResponseDTO> pagosDe(Long comandaId) {
		if (comandaId == null) {
			throw new BadRequestException("El id de la comanda es obligatorio");
		}
		return mapper.toPagoResponseList(pagoRepository.findByComandaIdOrderByFechaHoraAsc(comandaId));
	}

	@Override
	public List<TutorDelDiaResponseDTO> tutoresDelDia(LocalDate fecha) {
		// Sin reglas propias: "actividad del dia" tiene una sola definicion y la
		// comparte con el checkout de egreso.
		return jornadaDelDiaService.tutoresDelDia(fecha);
	}

	@Override
	public CierreCajaResponseDTO cerrarJornada(LocalDate fecha) {
		LocalDate dia = fecha != null ? fecha : LocalDate.now();
		LocalDateTime desde = dia.atStartOfDay();
		LocalDateTime hasta = dia.plusDays(1).atStartOfDay();

		// Se arrancan los cinco metodos en cero para que el cierre no venga con
		// huecos: el mostrador tiene que ver que el CREDITO dio 0, no que no
		// existe. Distinguir "no hubo pagos con credito" de "no se consulto" es
		// justo lo que se necesita cuando se busca un faltante.
		Map<String, BigDecimal> porMetodo = new LinkedHashMap<>();
		for (Pago.MetodoPago metodo : Pago.MetodoPago.values()) {
			porMetodo.put(metodo.name(), CERO);
		}

		for (Object[] fila : pagoRepository.totaledPorMetodoPago(desde, hasta)) {
			porMetodo.put(nombreDeMetodo(fila[0]).name(),
					((BigDecimal) fila[1]).setScale(2, RoundingMode.HALF_UP));
		}

		// Todas las cifras se agregan en MySQL: la jornada no se trae fila por
		// fila para sumar en memoria. El extracto se proyecta plano y acotado al
		// mismo rango semiabierto.
		BigDecimal totalCobrado = pagoRepository.sumarTotalDelDia(desde, hasta)
				.setScale(2, RoundingMode.HALF_UP);
		long cantidadPagos = pagoRepository.contarPagosDelDia(desde, hasta);
		long comandasCobradas = pagoRepository.contarComandasCobradas(desde, hasta);
		BigDecimal saldosPendientes = comandaRepository.sumarSaldoPendienteDelDia(desde, hasta)
				.setScale(2, RoundingMode.HALF_UP);

		List<MovimientoCajaDTO> movimientos = pagoRepository.movimientosDelDia(desde, hasta)
				.stream()
				.map(this::aMovimiento)
				.toList();

		return new CierreCajaResponseDTO(dia, totalCobrado, (int) cantidadPagos,
				(int) comandasCobradas, saldosPendientes, porMetodo, movimientos);
	}

	/**
	 * Traduce la proyeccion JPQL al DTO plano.
	 *
	 * <p>El mapeo se hace en Java y no con una expresion de constructor JPQL
	 * porque los enums viajan al DTO como su nombre: una proyeccion sin tipo
	 * declarado puede devolverlos como enum o como texto segun el driver, y el
	 * mismo motivo por el que {@link #nombreDeMetodo} existe aplica aca.</p>
	 */
	private MovimientoCajaDTO aMovimiento(MovimientoCajaProyeccion fila) {
		return new MovimientoCajaDTO(
				fila.getId(),
				fila.getFechaHora(),
				fila.getFechaEmision(),
				fila.getMetodoPago() != null ? fila.getMetodoPago().name() : null,
				fila.getMonto(),
				fila.getConcepto() != null ? fila.getConcepto().name() : null,
				fila.getNombreTutor(),
				fila.getNombrePagador(),
				fila.getNombreMascota(),
				fila.getEstadoComanda() != null ? fila.getEstadoComanda().name() : null);
	}

	/**
	 * Normaliza el metodo de pago de una fila agregada.
	 *
	 * <p>Una proyeccion JPQL sin tipo declarado ({@code Object[]}) puede devolver
	 * el enum como instancia o como texto segun como lo resuelva el driver, asi
	 * que se aceptan las dos formas. Sin esto, el cierre de caja dependeria de
	 * si el servidor devuelve la columna como VARCHAR o como el enum de Java.</p>
	 */
	private Pago.MetodoPago nombreDeMetodo(Object valor) {
		if (valor instanceof Pago.MetodoPago metodo) {
			return metodo;
		}
		return Pago.MetodoPago.valueOf(String.valueOf(valor));
	}

	// ------------------------------------------------------------- internos

	/**
	 * Toma la comanda con lock de escritura.
	 *
	 * <p>El saldo es un dato compartido entre cajas. Sin este lock, dos cajas
	 * que cobran a la vez leen el mismo saldo, las dos ven que alcanza y las
	 * dos lo restan: la comanda queda con saldo negativo y se habria cobrado
	 * mas de lo facturado.</p>
	 */
	private ComandaCobro bloquearComanda(Long comandaId) {
		if (comandaId == null) {
			throw new BadRequestException("El id de la comanda es obligatorio");
		}

		try {
			return comandaRepository.bloquearPorId(comandaId)
					.orElseThrow(() -> new ResourceNotFoundException(
							"No existe una comanda con id " + comandaId));
		} catch (CannotAcquireLockException ex) {
			// No queda lock huerfano: en InnoDB los bloqueos de fila se liberan al
			// confirmar o revertir la transaccion que los tomo. El que retiene la
			// comanda es otra caja viva, que lo soltara al terminar su operacion.
			throw new BusinessConflictException(
					"La comanda " + comandaId + " esta siendo procesada por otra caja. "
							+ "Reintente en unos segundos.");
		}
	}

	private void aplicarPago(ComandaCobro comanda, PagoRequestDTO.DetallePagoDTO detalle,
			BigDecimal saldoAntes, Tutor pagador) {
		BigDecimal monto = detalle.monto().setScale(2, RoundingMode.HALF_UP);

		if (monto.compareTo(BigDecimal.ZERO) <= 0) {
			throw new BadRequestException("El monto del pago debe ser mayor a cero");
		}
		if (monto.compareTo(saldoAntes) > 0) {
			throw new BadRequestException(String.format(
					"El pago de %s excede el saldo pendiente de la comanda (%s). "
							+ "El total de los pagos no puede superar el monto facturado.",
					monto, saldoAntes));
		}

		Pago pago = new Pago();
		pago.setComanda(comanda);
		pago.setMetodoPago(Pago.MetodoPago.valueOf(detalle.metodoPago().name()));
		pago.setMonto(monto);
		pago.setFechaHora(LocalDateTime.now());
		pago.setTutorPagador(pagador);

		// registrarPago vuelve a validar el saldo, descuenta y recalcula el
		// estado. Los chequeos de arriba ya cubren la mayor parte de los casos,
		// pero la entidad lanza IllegalArgumentException, y sin traducir eso
		// llegaria al cliente como 500: una regla de negocio reportada como
		// error del servidor manda a revisar el log en vez de a corregir la
		// carga en el mostrador.
		try {
			comanda.registrarPago(pago);
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException(ex.getMessage());
		}
	}

	/**
	 * Quien esta pagando, segun lo que declara la peticion.
	 *
	 * <p>Sin {@code tutorId} se asume el deudor de la comanda, que es el caso
	 * normal y el unico posible antes de que existiera el campo: los pagos ya
	 * cargados no tienen pagador y no se van a inventar.</p>
	 *
	 * <p>Con {@code tutorId} se exige que sea el deudor o un tutor vinculado a la
	 * mascota de la comanda. Se valida la <b>vinculacion</b> y no el
	 * {@code autorizado_retiro}: el flag gobierna quien puede llevarse al animal
	 * (regla 2.4) y el cobro es otra puerta. Un cotutor al que se le prohibio
	 * retirar al perro es exactamente a quien se le acepta el pago cuando vino a
	 * saldar la cuenta de la familia; exigir el flag en el cobro rechazaria el caso
	 * real y dejaria la comanda impaga.</p>
	 *
	 * <p>Sin mascota no hay vinculo contra el cual comprobar: ahi solo el deudor
	 * puede pagar. Admitir cualquier tutor seria aceptar que un desconocido salde
	 * una comanda ajena.</p>
	 *
	 * <p>Que exista en {@code tutor} no alcanza: sin el vinculo, o con el vinculo y
	 * el flag en false, la respuesta es la misma por decision de negocio. La
	 * diferencia solo importa para diagnosticar, igual que en el semaforo de egreso.</p>
	 */
	private Tutor resolverPagador(ComandaCobro comanda, Long tutorId) {
		if (tutorId == null) {
			return comanda.getTutor();
		}

		Tutor tutor = tutorRepository.findById(tutorId)
				.orElseThrow(() -> ResourceNotFoundException.tutor(tutorId));

		if (tutorId.equals(comanda.getTutor().getId())) {
			return tutor;
		}

		Mascota mascota = comanda.getMascota();
		if (mascota == null) {
			throw new BadRequestException(
					"La comanda " + comanda.getId() + " no tiene mascota asociada, "
							+ "asi que solo puede pagarla su titular " + comanda.getTutor().getNombreCompleto());
		}

		boolean vinculado = vinculoRepository
				.findByTutorIdAndMascotaId(tutorId, mascota.getId())
				.isPresent();
		if (!vinculado) {
			throw new BadRequestException(
					"El tutor " + tutor.getNombreCompleto() + " no esta vinculado a "
							+ mascota.getNombre() + ", asi que no puede pagar esta comanda. "
							+ "Pagala el tutor " + comanda.getTutor().getNombreCompleto()
							+ " o vinculen al tutor a la mascota desde el padron.");
		}

		return tutor;
	}
}