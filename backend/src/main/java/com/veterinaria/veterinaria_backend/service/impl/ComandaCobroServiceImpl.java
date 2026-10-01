package com.veterinaria.veterinaria_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.ComandaCobroRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ComandaCobroResponseDTO;
import com.veterinaria.veterinaria_backend.dto.DeudaTutorResponseDTO;
import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.Tarifa;
import com.veterinaria.veterinaria_backend.entity.Tutor;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.mapper.CobroMapper;
import com.veterinaria.veterinaria_backend.repository.ComandaCobroRepository;
import com.veterinaria.veterinaria_backend.repository.MascotaRepository;
import com.veterinaria.veterinaria_backend.repository.TarifaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorRepository;
import com.veterinaria.veterinaria_backend.service.ComandaCobroService;

/**
 * Implementacion transaccional de {@link ComandaCobroService}.
 *
 * <p>Con {@code open-in-view=false} las relaciones LAZY se resuelven dentro de
 * la transaccion del servicio: tanto {@code comanda.getTutor()} al proyectar el
 * DTO como {@code comanda.getPagos()} tienen que ocurrir aca adentro.</p>
 */
@Service
@Transactional(readOnly = true)
public class ComandaCobroServiceImpl implements ComandaCobroService {

	/**
	 * Estados que generan deuda. PAGADA esta saldada y CANCELADA no debe nada:
	 * sumarlas en el arqueo de morosidad inflaria la deuda del local.
	 */
	private static final List<ComandaCobro.EstadoComanda> ESTADOS_CON_DEUDA = List.of(
			ComandaCobro.EstadoComanda.PENDIENTE,
			ComandaCobro.EstadoComanda.PARCIAL);

	private final ComandaCobroRepository comandaRepository;
	private final TutorRepository tutorRepository;
	private final MascotaRepository mascotaRepository;
	private final TarifaRepository tarifaRepository;
	private final CobroMapper mapper;

	public ComandaCobroServiceImpl(ComandaCobroRepository comandaRepository,
			TutorRepository tutorRepository,
			MascotaRepository mascotaRepository,
			TarifaRepository tarifaRepository,
			CobroMapper mapper) {
		this.comandaRepository = comandaRepository;
		this.tutorRepository = tutorRepository;
		this.mascotaRepository = mascotaRepository;
		this.tarifaRepository = tarifaRepository;
		this.mapper = mapper;
	}

	@Override
	public List<ComandaCobroResponseDTO> listarPorTutor(Long tutorId, String estado) {
		obtenerTutor(tutorId);

		if (estado == null || estado.isBlank()) {
			return mapper.toComandaResponseList(
					comandaRepository.findByTutorIdOrderByFechaEmisionDesc(tutorId));
		}
		return mapper.toComandaResponseList(
				comandaRepository.findByTutorIdAndEstadoOrderByFechaEmisionDesc(
						tutorId, parseEstado(estado)));
	}

	@Override
	public ComandaCobroResponseDTO buscarPorId(Long id) {
		return mapper.toResponse(obtenerComanda(id));
	}

	@Override
	@Transactional
	public ComandaCobroResponseDTO emitir(ComandaCobroRequestDTO request) {
		Tutor tutor = obtenerTutor(request.tutorId());
		Mascota mascota = request.mascotaId() == null ? null : obtenerMascota(request.mascotaId());
		BigDecimal monto = precioDelConcepto(request.concepto());

		ComandaCobro comanda = new ComandaCobro();
		comanda.setConcepto(parseConcepto(request.concepto()));
		comanda.setMontoTotal(monto);
		comanda.setSaldoPendiente(monto);
		comanda.setEstado(monto.compareTo(BigDecimal.ZERO) > 0
				? ComandaCobro.EstadoComanda.PENDIENTE
				: ComandaCobro.EstadoComanda.PAGADA);
		comanda.setTutor(tutor);
		comanda.setMascota(mascota);

		return mapper.toResponse(comandaRepository.save(comanda));
	}

	@Override
	@Transactional
	public ComandaCobroResponseDTO cancelar(Long id) {
		ComandaCobro comanda = obtenerComanda(id);
		if (comanda.getEstado() == ComandaCobro.EstadoComanda.CANCELADA) {
			throw new BadRequestException("La comanda " + id + " ya estaba cancelada");
		}
		if (comanda.getEstado() == ComandaCobro.EstadoComanda.PAGADA) {
			throw new BadRequestException(
					"La comanda " + id + " esta PAGADA: para revertir un cobro hay que emitir la nota de credito, no cancelar");
		}

		comanda.setEstado(ComandaCobro.EstadoComanda.CANCELADA);
		return mapper.toResponse(comanda);
	}

	@Override
	public DeudaTutorResponseDTO consultarDeuda(Long tutorId) {
		Tutor tutor = obtenerTutor(tutorId);

		List<ComandaCobro> pendientes =
				comandaRepository.findByTutorIdAndEstadoInOrderByFechaEmisionAsc(tutorId, ESTADOS_CON_DEUDA);

		// El total se recalcula sumando y no leyendo sumarDeudaPendiente(): asi el
		// detalle y el total salen de la misma lectura y no pueden contradecirse.
		BigDecimal total = pendientes.stream()
				.map(ComandaCobro::getSaldoPendiente)
				.filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);

		LocalDate hoy = LocalDate.now();
		List<DeudaTutorResponseDTO.ComandaPendienteDTO> detalle = pendientes.stream()
				.map(comanda -> new DeudaTutorResponseDTO.ComandaPendienteDTO(
						comanda.getId(),
						comanda.getConcepto() != null ? comanda.getConcepto().name() : null,
						comanda.getSaldoPendiente(),
						comanda.getFechaEmision() != null
								? comanda.getFechaEmision().toLocalDate()
								: null,
						comanda.getFechaEmision() != null
								? ChronoUnit.DAYS.between(comanda.getFechaEmision().toLocalDate(), hoy)
								: 0))
				.toList();

		return new DeudaTutorResponseDTO(
				tutor.getId(),
				tutor.getNombreCompleto(),
				total.compareTo(BigDecimal.ZERO) > 0,
				total,
				pendientes.size(),
				detalle);
	}

	@Override
	public Map<String, BigDecimal> listarTarifas() {
		Map<String, BigDecimal> precios = new LinkedHashMap<>();
		for (Tarifa tarifa : tarifaRepository.findByVigenteTrueOrderByConceptoAsc()) {
			precios.put(tarifa.getConcepto().name(), tarifa.getMonto());
		}
		return precios;
	}

	@Override
	@Transactional
	public BigDecimal actualizarTarifa(String concepto, BigDecimal monto) {
		if (monto == null || monto.compareTo(BigDecimal.ZERO) < 0) {
			throw new BadRequestException("El monto de la tarifa debe ser mayor o igual a 0");
		}

		ComandaCobro.Concepto enumConcepto = parseConcepto(concepto);
		BigDecimal normalizado = monto.setScale(2, RoundingMode.HALF_UP);

		// Se actualiza la fila existente en vez de insertar otra: la constraint
		// UNIQUE sobre concepto solo admite una tarifa vigente por tipo.
		Tarifa tarifa = tarifaRepository.findByConcepto(enumConcepto)
				.orElseGet(() -> {
					Tarifa nueva = new Tarifa();
					nueva.setConcepto(enumConcepto);
					return nueva;
				});

		tarifa.setMonto(normalizado);
		tarifa.setVigente(true);
		tarifaRepository.save(tarifa);

		return normalizado;
	}

	// ------------------------------------------------------------- internos

	/**
	 * Precio vigente del concepto, con dos decimales.
	 *
	 * <p>Se lanza excepcion si no hay tarifa cargada en lugar de asumir un
	 * default: emitir una comanda de $0 por falta de configuracion es peor que
	 * fallar, porque el cliente se va pensando que debe nada.</p>
	 */
	private BigDecimal precioDelConcepto(String concepto) {
		ComandaCobro.Concepto enumConcepto = parseConcepto(concepto);

		return tarifaRepository.findByConcepto(enumConcepto)
				.map(tarifa -> tarifa.getMonto() == null
						? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
						: tarifa.getMonto().setScale(2, RoundingMode.HALF_UP))
				.orElseThrow(() -> new BadRequestException(
						"No hay tarifa cargada para el concepto " + enumConcepto
								+ ". Carguela antes de emitir la comanda."));
	}

	private ComandaCobro.Concepto parseConcepto(String concepto) {
		try {
			return ComandaCobro.Concepto.valueOf(concepto.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException("Concepto de cobro invalido: " + concepto
					+ ". Valores admitidos: " + Arrays.toString(ComandaCobro.Concepto.values()));
		}
	}

	private ComandaCobro.EstadoComanda parseEstado(String estado) {
		try {
			return ComandaCobro.EstadoComanda.valueOf(estado.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			throw new BadRequestException("Estado de comanda invalido: " + estado
					+ ". Valores admitidos: "
					+ Arrays.toString(ComandaCobro.EstadoComanda.values()));
		}
	}

	private ComandaCobro obtenerComanda(Long id) {
		if (id == null) {
			throw new BadRequestException("El id de la comanda es obligatorio");
		}
		return comandaRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No existe una comanda con id " + id));
	}

	private Tutor obtenerTutor(Long id) {
		if (id == null) {
			throw new BadRequestException("El id del tutor es obligatorio");
		}
		return tutorRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.tutor(id));
	}

	private Mascota obtenerMascota(Long id) {
		return mascotaRepository.findById(id)
				.orElseThrow(() -> ResourceNotFoundException.mascota(id));
	}
}