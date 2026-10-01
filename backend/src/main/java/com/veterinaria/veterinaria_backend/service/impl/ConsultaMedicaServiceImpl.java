package com.veterinaria.veterinaria_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.ConsultaMedicaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ConsultaMedicaResponseDTO;
import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.entity.ConsultaMedica;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.Profesional;
import com.veterinaria.veterinaria_backend.entity.Tutor;
import com.veterinaria.veterinaria_backend.entity.TutorMascota;
import com.veterinaria.veterinaria_backend.entity.TurnoClinico;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.mapper.ConsultaMapper;
import com.veterinaria.veterinaria_backend.repository.ComandaCobroRepository;
import com.veterinaria.veterinaria_backend.repository.ConsultaMedicaRepository;
import com.veterinaria.veterinaria_backend.repository.MascotaRepository;
import com.veterinaria.veterinaria_backend.repository.ProfesionalRepository;
import com.veterinaria.veterinaria_backend.repository.TarifaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorMascotaRepository;
import com.veterinaria.veterinaria_backend.repository.TurnoClinicoRepository;
import com.veterinaria.veterinaria_backend.service.ConsultaMedicaService;

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
	private final ConsultaMapper mapper;

	public ConsultaMedicaServiceImpl(ConsultaMedicaRepository consultaRepository,
			TurnoClinicoRepository turnoRepository,
			MascotaRepository mascotaRepository,
			ProfesionalRepository profesionalRepository,
			TutorMascotaRepository tutorMascotaRepository,
			TarifaRepository tarifaRepository,
			ComandaCobroRepository comandaRepository,
			ConsultaMapper mapper) {
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
	public ConsultaMedicaResponseDTO registrar(ConsultaMedicaRequestDTO request) {
		Mascota mascota = obtenerMascota(request.mascotaId());
		Profesional profesional = obtenerProfesional(request.profesionalId());

		// Idempotencia por turno: una consulta cerrada no se vuelve a cargar.
		// Sin este chequeo, un doble clic del veterinario emitiria dos fichas y
		// dos comandas por la misma atencion.
		if (request.turnoClinicoId() != null
				&& consultaRepository.existsByTurnoClinicoId(request.turnoClinicoId())) {
			throw new BadRequestException(
					"El turno " + request.turnoClinicoId() + " ya tiene una ficha clinica registrada");
		}

		ConsultaMedica consulta = new ConsultaMedica();
		consulta.setMascota(mascota);
		consulta.setProfesional(profesional);
		if (request.turnoClinicoId() != null) {
			consulta.setTurnoClinico(obtenerTurno(request.turnoClinicoId()));
		}
		consulta.setFechaAtencion(request.fechaAtencion() != null
				? request.fechaAtencion()
				: LocalDateTime.now());
		consulta.setAnamnesis(request.anamnesis());
		consulta.setDiagnostico(request.diagnostico());
		consulta.setTratamiento(request.tratamiento());
		consulta.setConductaObservada(traducirConducta(request.conductaObservada()));

		ConsultaMedica guardada = consultaRepository.save(consulta);

		// Ajeno al desconeccionado clinico: la ficha ya esta, ahora se emite el
		// cobro al mostrador con el precio congelado de la tarifa vigente.
		ComandaCobro comanda = emitirComanda(guardada, mascota);

		return mapper.toResponse(guardada, comanda.getId());
	}

	@Override
	public ConsultaMedicaResponseDTO buscarPorId(Long id) {
		ConsultaMedica consulta = obtenerConsulta(id);
		return mapper.toResponse(consulta, comandaDe(id));
	}

	@Override
	public List<ConsultaMedicaResponseDTO> listarPorMascota(Long mascotaId) {
		obtenerMascota(mascotaId);
		return mapper.toResponseList(consultaRepository.findByMascotaIdOrderByFechaAtencionDesc(mascotaId));
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
	private ComandaCobro emitirComanda(ConsultaMedica consulta, Mascota mascota) {
		Tutor tutor = tutorCobrador(mascota);

		BigDecimal monto = precioConsultaClinica();

		ComandaCobro comanda = new ComandaCobro();
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
}