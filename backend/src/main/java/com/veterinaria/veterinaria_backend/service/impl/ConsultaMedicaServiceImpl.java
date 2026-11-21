package com.veterinaria.veterinaria_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.ConsultaMedicaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ConsultaMedicaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.ConsultaMedicaResponseDTO.MedicamentoAplicadoDTO;
import com.veterinaria.veterinaria_backend.dto.MedicamentoResponseDTO;
import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.entity.ConsultaMedica;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.MedicamentoUsadoFicha;
import com.veterinaria.veterinaria_backend.entity.MovimientoStockMedicamento;
import com.veterinaria.veterinaria_backend.entity.MovimientoStockMedicamento.TipoMovimiento;
import com.veterinaria.veterinaria_backend.entity.Profesional;
import com.veterinaria.veterinaria_backend.entity.Tutor;
import com.veterinaria.veterinaria_backend.entity.TutorMascota;
import com.veterinaria.veterinaria_backend.entity.ComandaItem;
import com.veterinaria.veterinaria_backend.entity.TurnoClinico;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.mapper.ConsultaMapper;
import com.veterinaria.veterinaria_backend.repository.ComandaCobroRepository;
import com.veterinaria.veterinaria_backend.repository.ConsultaMedicaRepository;
import com.veterinaria.veterinaria_backend.repository.MascotaRepository;
import com.veterinaria.veterinaria_backend.repository.MedicamentoUsadoFichaRepository;
import com.veterinaria.veterinaria_backend.repository.MovimientoStockMedicamentoRepository;
import com.veterinaria.veterinaria_backend.repository.ProfesionalRepository;
import com.veterinaria.veterinaria_backend.repository.TarifaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorMascotaRepository;
import com.veterinaria.veterinaria_backend.repository.TurnoClinicoRepository;
import com.veterinaria.veterinaria_backend.service.ConsultaMedicaService;
import com.veterinaria.veterinaria_backend.service.MedicamentoService;

/**
 * Implementacion transaccional de {@link ConsultaMedicaService}.
 *
 * <p><b>Desacople clinico-contable (AGENTS.md 2.3).</b> Este servicio es el
 * puente entre los dos mundos: recibe una ficha sin un solo peso y emite la
 * comanda de cobro que el mostrador liquidara despues. La ficha no sabe que la
 * comanda existe; la comanda no sabe nada del diagnostico.</p>
 *
 * <p>Ambas escrituras van en la misma transaccion. Si la comanda no se puede
 * emitir, la ficha tampoco queda registrada: una atencion sin cobro no es
 * recuperable de otra manera y dejaria al local asumiendo el costo.</p>
 */
@Service
@Transactional(readOnly = true)
public class ConsultaMedicaServiceImpl implements ConsultaMedicaService {

	private final ConsultaMedicaRepository consultaRepository;
	private final TurnoClinicoRepository turnoRepository;
	private final MascotaRepository mascotaRepository;
	private final ProfesionalRepository profesionalRepository;
	private final TutorMascotaRepository tutorMascotaRepository;
	private final TarifaRepository tarifaRepository;
	private final ComandaCobroRepository comandaRepository;
	private final MedicamentoService medicamentoService;
	private final MedicamentoUsadoFichaRepository medicamentoUsadoRepository;
	private final MovimientoStockMedicamentoRepository movimientoRepository;
	private final ConsultaMapper mapper;

	public ConsultaMedicaServiceImpl(ConsultaMedicaRepository consultaRepository,
			TurnoClinicoRepository turnoRepository,
			MascotaRepository mascotaRepository,
			ProfesionalRepository profesionalRepository,
			TutorMascotaRepository tutorMascotaRepository,
			TarifaRepository tarifaRepository,
			ComandaCobroRepository comandaRepository,
			MedicamentoService medicamentoService,
			MedicamentoUsadoFichaRepository medicamentoUsadoRepository,
			MovimientoStockMedicamentoRepository movimientoRepository,
			ConsultaMapper mapper) {
		this.medicamentoService = medicamentoService;
		this.medicamentoUsadoRepository = medicamentoUsadoRepository;
		this.movimientoRepository = movimientoRepository;
		this.consultaRepository = consultaRepository;
		this.turnoRepository = turnoRepository;
		this.mascotaRepository = mascotaRepository;
		this.profesionalRepository = profesionalRepository;
		this.tutorMascotaRepository = tutorMascotaRepository;
		this.tarifaRepository = tarifaRepository;
		this.comandaRepository = comandaRepository;
		this.mapper = mapper;
	}

	@Override
	@Transactional
	public ConsultaMedicaResponseDTO registrar(ConsultaMedicaRequestDTO request, Long usuarioId) {
		// Sin mascota solo se atiende una urgencia que todavia no tiene datos (se valida abajo).
		Mascota mascota = request.mascotaId() == null ? null : obtenerMascota(request.mascotaId());
		Profesional profesional = obtenerProfesional(request.profesionalId());

		// Idempotencia por turno: una consulta cerrada no se vuelve a cargar.
		// Sin este chequeo, un doble clic del veterinario emitiria dos fichas y
		// dos comandas por la misma atencion.
		if (request.turnoClinicoId() != null
				&& consultaRepository.existsByTurnoClinicoId(request.turnoClinicoId())) {
			throw new BadRequestException(
					"El turno " + request.turnoClinicoId() + " ya tiene una ficha clinica registrada");
		}

		// Todo lo que puede rechazar el pedido se resuelve antes de escribir.
		TurnoClinico turno = request.turnoClinicoId() == null ? null : turnoAtendible(request.turnoClinicoId(), mascota);
		if (mascota == null && (turno == null || !turno.isUrgencia())) {
			throw new BadRequestException("El id de la mascota es obligatorio");
		}
		List<MedicamentoUsado> medicamentos = resolverMedicamentos(request.medicamentos());

		ConsultaMedica consulta = new ConsultaMedica();
		consulta.setMascota(mascota);
		consulta.setProfesional(profesional);
		if (turno != null) {
			consulta.setTurnoClinico(turno);
			// La urgencia se agenda sin profesional: lo define quien la atiende.
			if (turno.getProfesional() == null) {
				turno.setProfesional(profesional);
			}
			// Si el veterinario ya sabe de quien es, la urgencia queda con sus datos.
			if (turno.getMascota() == null && mascota != null) {
				turno.setMascota(mascota);
			}
			// Guardar la ficha es dar la atencion por terminada.
			turno.setEstado(TurnoClinico.EstadoTurno.ATENDIDO);
		}
		consulta.setFechaAtencion(request.fechaAtencion() != null
				? request.fechaAtencion()
				: LocalDateTime.now());
		consulta.setAnamnesis(request.anamnesis());
		consulta.setDiagnostico(request.diagnostico());
		consulta.setTratamiento(request.tratamiento());
		if (request.pesoKg() != null) {
			// El peso se toma al atender: queda en la ficha y pasa a ser el peso actual.
			consulta.setPesoKg(request.pesoKg().doubleValue());
			mascota.setPesoKg(request.pesoKg());
		}
		consulta.setConductaObservada(traducirConducta(request.conductaObservada()));

		ConsultaMedica guardada = consultaRepository.save(consulta);

		// Snapshot clinico: nombre y presentacion se copian aca, antes de cualquier
		// cambio de catalogo. Va en la misma transaccion que ficha, stock y comanda.
		List<MedicamentoAplicadoDTO> aplicados = guardarSnapshot(guardada, medicamentos);

		// El consumo descuenta stock y deja el movimiento con el id de la ficha.
		// Si no hay stock, excepcion y rollback: ni ficha ni comanda quedan a medias.
		descontarStock(guardada.getId(), medicamentos, usuarioId);

		// Ajeno al desacople clinico: la ficha ya esta, ahora se emite el
		// cobro al mostrador con el precio congelado de la tarifa vigente.
		ComandaCobro comanda = emitirComanda(guardada, mascota, medicamentos);

		return mapper.toResponse(guardada, comanda.getId(), aplicados);
	}

	@Override
	public ConsultaMedicaResponseDTO buscarPorId(Long id) {
		ConsultaMedica consulta = obtenerConsulta(id);
		List<MedicamentoAplicadoDTO> aplicados = medicamentosDe(List.of(id))
				.getOrDefault(id, List.of());
		return mapper.toResponse(consulta, comandaDe(id), aplicados);
	}

	@Override
	public List<ConsultaMedicaResponseDTO> listarPorMascota(Long mascotaId) {
		obtenerMascota(mascotaId);
		List<ConsultaMedica> consultas = consultaRepository.findByMascotaIdOrderByFechaAtencionDesc(mascotaId);
		Map<Long, List<MedicamentoAplicadoDTO>> aplicados = medicamentosDe(
				consultas.stream().map(ConsultaMedica::getId).toList());
		return consultas.stream()
				.map(consulta -> mapper.toResponse(consulta, null,
						aplicados.getOrDefault(consulta.getId(), List.of())))
				.toList();
	}

	@Override
	@Transactional
	public void asignarMascotaDeUrgencia(Long turnoId, Long mascotaId) {
		Mascota mascota = obtenerMascota(mascotaId);
		consultaRepository.findByTurnoClinicoId(turnoId)
				.filter(consulta -> consulta.getMascota() == null)
				.ifPresent(consulta -> {
					consulta.setMascota(mascota);
					comandaRepository.findByConsultaMedicaId(consulta.getId()).ifPresent(comanda -> {
						comanda.setMascota(mascota);
						comanda.setTutor(tutorCobrador(mascota));
					});
				});
	}

	@Override
	public Long comandaDe(Long consultaId) {
		obtenerConsulta(consultaId);
		return comandaRepository.findByConsultaMedicaId(consultaId)
				.map(ComandaCobro::getId)
				.orElse(null);
	}

	// ------------------------------------------------------------- internos

	/**
	 * Emite la comanda de la consulta clinica.
	 *
	 * <p>El tutor deudor es el que puede retirar a la mascota (regla 2.4): no
	 * cualquier vinculo sirve, porque un cotutor sin autorizacion de retiro no
	 * es quien asume la deuda.</p>
	 */
	private ComandaCobro emitirComanda(ConsultaMedica consulta, Mascota mascota,
			List<MedicamentoUsado> medicamentos) {
		// Urgencia sin datos: la comanda nace sin titular y no se puede cobrar hasta completarlos.
		Tutor tutor = mascota == null ? null : tutorCobrador(mascota);

		ComandaCobro comanda = new ComandaCobro();
		// Una linea por la consulta y una por cada medicamento; el total es su suma.
		// Nombre y precio se copian aca: cambios posteriores del catalogo no mueven la comanda.
		comanda.agregarItem(item("Consulta clínica", 1, precioConsultaClinica()));
		for (MedicamentoUsado usado : medicamentos) {
			comanda.agregarItem(item(usado.medicamento().nombre(), usado.cantidad(),
					usado.medicamento().precio()));
		}
		BigDecimal monto = comanda.getItems().stream()
				.map(ComandaItem::getSubtotal)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);

		comanda.setConcepto(ComandaCobro.Concepto.CONSULTA_CLINICA);
		comanda.setMontoTotal(monto);
		comanda.setSaldoPendiente(monto);
		comanda.setEstado(ComandaCobro.EstadoComanda.PENDIENTE);
		comanda.setTutor(tutor);
		comanda.setMascota(mascota);
		comanda.setConsultaMedica(consulta);

		return comandaRepository.save(comanda);
	}

	/** Tarifa vigente de consulta clinica. */
	private BigDecimal precioConsultaClinica() {
		return tarifaRepository.findByConcepto(ComandaCobro.Concepto.CONSULTA_CLINICA)
				.map(tarifa -> tarifa.getMonto() == null
						? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
						: tarifa.getMonto().setScale(2, RoundingMode.HALF_UP))
				.orElseThrow(() -> new BadRequestException(
						"No hay tarifa cargada para CONSULTA_CLINICA. Carguela antes de registrar una consulta."));
	}

	/**
	 * Tutor responsable de la deuda: el vinculado con autorizacion de retiro.
	 *
	 * <p>Si ninguno esta autorizado, la consulta se rechaza en vez de elegir un
	 * tutor al azar: emitir el cobro contra la persona equivocada produce un
	 * moroso falso y una perdida de plata.</p>
	 */
	private Tutor tutorCobrador(Mascota mascota) {
		List<Tutor> autorizados = tutorMascotaRepository
				.findByMascotaIdAndAutorizadoRetiroTrue(mascota.getId()).stream()
				.map(TutorMascota::getTutor)
				.filter(Objects::nonNull)
				.toList();

		if (autorizados.isEmpty()) {
			throw new BadRequestException(
					"La mascota " + mascota.getId() + " no tiene ningun tutor autorizado a retirar: "
							+ "no se puede emitir la comanda de cobro");
		}
		return autorizados.get(0);
	}

	private ConsultaMedica.ConductaObservada traducirConducta(
			ConsultaMedicaRequestDTO.ConductaRequestDTO conducta) {
		if (conducta == null) {
			return ConsultaMedica.ConductaObservada.TRANQUILO;
		}
		return ConsultaMedica.ConductaObservada.valueOf(conducta.name());
	}

	private ConsultaMedica obtenerConsulta(Long id) {
		if (id == null) {
			throw new BadRequestException("El id de la consulta es obligatorio");
		}
		return consultaRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No existe una consulta con id " + id));
	}

	private Mascota obtenerMascota(Long id) {
		if (id == null) {
			throw new BadRequestException("El id de la mascota es obligatorio");
		}
		return mascotaRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.mascota(id));
	}

	private Profesional obtenerProfesional(Long id) {
		if (id == null) {
			throw new BadRequestException("El id del profesional es obligatorio");
		}
		return profesionalRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No existe un profesional con id " + id));
	}

	private TurnoClinico obtenerTurno(Long id) {
		return turnoRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No existe un turno con id " + id));
	}

	/**
	 * El turno tiene que ser de esta mascota y no estar cancelado. Una urgencia
	 * sin datos se puede atender con o sin mascota.
	 */
	private TurnoClinico turnoAtendible(Long turnoId, Mascota mascota) {
		TurnoClinico turno = obtenerTurno(turnoId);
		if (turno.getMascota() != null) {
			if (mascota == null) {
				throw new BadRequestException("El id de la mascota es obligatorio");
			}
			if (!Objects.equals(turno.getMascota().getId(), mascota.getId())) {
				throw new BadRequestException("El turno " + turnoId + " es de otra mascota");
			}
		}
		if (turno.getEstado() == TurnoClinico.EstadoTurno.CANCELADO) {
			throw new BadRequestException("El turno " + turnoId + " esta cancelado: no se puede atender");
		}
		return turno;
	}

	/**
	 * Medicamentos del pedido con su snapshot de catalogo: 404 si no existe,
	 * 400 si esta desactivado. El precio se lee aca, pero nunca viaja en el
	 * payload de la ficha: solo se copia a la comanda.
	 */
	private List<MedicamentoUsado> resolverMedicamentos(
			List<ConsultaMedicaRequestDTO.MedicamentoUsadoDTO> pedidos) {
		if (pedidos == null) {
			return List.of();
		}
		return pedidos.stream().map(pedido -> {
			MedicamentoResponseDTO medicamento = medicamentoService.obtener(pedido.medicamentoId());
			if (!medicamento.activo()) {
				throw new BadRequestException(
						"El medicamento " + medicamento.nombre() + " esta desactivado");
			}
			return new MedicamentoUsado(medicamento, pedido.cantidad());
		}).toList();
	}

	/** Descuenta cada medicamento usado en la misma transaccion de la ficha. */
	private void descontarStock(Long consultaId, List<MedicamentoUsado> medicamentos, Long usuarioId) {
		for (MedicamentoUsado usado : medicamentos) {
			medicamentoService.registrarConsumo(
					usado.medicamento().id(),
					usado.cantidad(),
					consultaId,
					usuarioId,
					"Consumo en ficha clinica #" + consultaId);
		}
	}

	/** Guarda el snapshot clinico y lo devuelve listo para la respuesta. */
	private List<MedicamentoAplicadoDTO> guardarSnapshot(ConsultaMedica consulta,
			List<MedicamentoUsado> medicamentos) {
		if (medicamentos.isEmpty()) {
			return List.of();
		}
		List<MedicamentoUsadoFicha> entidades = medicamentos.stream().map(usado -> {
			MedicamentoResponseDTO medicamento = usado.medicamento();
			MedicamentoUsadoFicha ficha = new MedicamentoUsadoFicha();
			ficha.setConsultaMedica(consulta);
			ficha.setMedicamentoId(medicamento.id());
			ficha.setNombre(medicamento.nombre());
			ficha.setPresentacion(medicamento.presentacion());
			ficha.setCantidad(usado.cantidad());
			return ficha;
		}).toList();
		return medicamentoUsadoRepository.saveAll(entidades).stream()
				.map(ConsultaMedicaServiceImpl::aAplicado)
				.toList();
	}

	/**
	 * Medicamentos aplicados por ficha. Las fichas nuevas tienen snapshot propio;
	 * las anteriores a este cambio se reconstruyen desde el egreso de stock, que
	 * siempre conserva el id del medicamento y la cantidad.
	 */
	private Map<Long, List<MedicamentoAplicadoDTO>> medicamentosDe(List<Long> consultaIds) {
		if (consultaIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<MedicamentoAplicadoDTO>> porConsulta = new HashMap<>();
		medicamentoUsadoRepository.findPorConsultas(consultaIds).forEach(ficha ->
				porConsulta.computeIfAbsent(ficha.getConsultaMedica().getId(), clave -> new ArrayList<>())
						.add(aAplicado(ficha)));

		List<Long> sinSnapshot = consultaIds.stream()
				.filter(id -> !porConsulta.containsKey(id))
				.toList();
		if (!sinSnapshot.isEmpty()) {
			movimientoRepository
					.findByTipoAndReferenciaIdInOrderByReferenciaIdAscIdAsc(
							TipoMovimiento.EGRESO_FICHA, sinSnapshot)
					.forEach(movimiento ->
							porConsulta.computeIfAbsent(movimiento.getReferenciaId(), clave -> new ArrayList<>())
									.add(aAplicado(movimiento)));
		}
		return porConsulta;
	}

	private static MedicamentoAplicadoDTO aAplicado(MedicamentoUsadoFicha ficha) {
		return new MedicamentoAplicadoDTO(ficha.getMedicamentoId(), ficha.getNombre(),
				ficha.getPresentacion(), ficha.getCantidad());
	}

	private static MedicamentoAplicadoDTO aAplicado(MovimientoStockMedicamento movimiento) {
		return new MedicamentoAplicadoDTO(
				movimiento.getMedicamento().getId(),
				movimiento.getMedicamento().getNombre(),
				movimiento.getMedicamento().getPresentacion(),
				movimiento.getCantidad());
	}

	private static ComandaItem item(String descripcion, int cantidad, BigDecimal precio) {
		BigDecimal unitario = precio.setScale(2, RoundingMode.HALF_UP);
		ComandaItem item = new ComandaItem();
		item.setDescripcion(descripcion);
		item.setCantidad(cantidad);
		item.setPrecioUnitario(unitario);
		item.setSubtotal(unitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP));
		return item;
	}

	private record MedicamentoUsado(MedicamentoResponseDTO medicamento, int cantidad) {
	}
}