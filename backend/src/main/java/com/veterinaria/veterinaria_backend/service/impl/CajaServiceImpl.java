package com.veterinaria.veterinaria_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.CierreCajaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.ComandaCobroResponseDTO;
import com.veterinaria.veterinaria_backend.dto.PagoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.PagoResponseDTO;
import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.entity.Pago;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.mapper.CobroMapper;
import com.veterinaria.veterinaria_backend.repository.ComandaCobroRepository;
import com.veterinaria.veterinaria_backend.repository.PagoRepository;
import com.veterinaria.veterinaria_backend.service.CajaService;

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
	private final CobroMapper mapper;

	public CajaServiceImpl(ComandaCobroRepository comandaRepository,
			PagoRepository pagoRepository,
			CobroMapper mapper) {
		this.comandaRepository = comandaRepository;
		this.pagoRepository = pagoRepository;
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

		BigDecimal saldoAntes = comanda.getSaldoPendiente();

		// Todos los pagos de la llamada se aplican en esta transaccion. Si el
		// segundo excede el saldo, la excepcion revierte el primero: el cliente
		// no puede quedar con medio cobro registrado.
		for (PagoRequestDTO.DetallePagoDTO detalle : request.pagos()) {
			aplicarPago(comanda, detalle, saldoAntes);
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
	public CierreCajaResponseDTO cerrarJornada(LocalDate fecha) {
		LocalDate dia = fecha != null ? fecha : LocalDate.now();
		LocalDateTime desde = dia.atStartOfDay();
		LocalDateTime hasta = dia.plusDays(1).atStartOfDay();

		List<Pago> pagos = pagoRepository.findByFechaHoraBetweenOrderByFechaHoraAsc(desde, hasta);

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

		BigDecimal totalCobrado = pagos.stream()
				.map(Pago::getMonto)
				.filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);

		long comandasCobradas = pagoRepository.contarComandasCobradas(desde, hasta);

		return new CierreCajaResponseDTO(dia, totalCobrado, pagos.size(),
				(int) comandasCobradas, porMetodo);
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
			BigDecimal saldoAntes) {
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
}