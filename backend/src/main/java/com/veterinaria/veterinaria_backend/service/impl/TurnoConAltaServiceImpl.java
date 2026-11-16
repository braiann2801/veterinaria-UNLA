package com.veterinaria.veterinaria_backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.CompletarDatosRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoClinicoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoClinicoResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoConAltaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TutorRequestDTO;
import com.veterinaria.veterinaria_backend.entity.Tutor;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.repository.TutorMascotaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorRepository;
import com.veterinaria.veterinaria_backend.service.ConsultaMedicaService;
import com.veterinaria.veterinaria_backend.service.MascotaService;
import com.veterinaria.veterinaria_backend.service.TurnoClinicoService;
import com.veterinaria.veterinaria_backend.service.TurnoConAltaService;
import com.veterinaria.veterinaria_backend.service.TutorService;

/**
 * Orquesta las altas existentes (tutor, mascota, turno) dentro de una sola
 * transaccion. No repite reglas: cada servicio aplica las suyas (DNI duplicado,
 * normalizacion, solapamiento 45+15), y cualquier excepcion deshace todo.
 */
@Service
public class TurnoConAltaServiceImpl implements TurnoConAltaService {

	private final TutorService tutorService;
	private final MascotaService mascotaService;
	private final TurnoClinicoService turnoService;
	private final TutorRepository tutorRepository;
	private final TutorMascotaRepository vinculoRepository;
	private final ConsultaMedicaService consultaService;

	public TurnoConAltaServiceImpl(TutorService tutorService, MascotaService mascotaService,
			TurnoClinicoService turnoService, TutorRepository tutorRepository,
			TutorMascotaRepository vinculoRepository, ConsultaMedicaService consultaService) {
		this.consultaService = consultaService;
		this.vinculoRepository = vinculoRepository;
		this.tutorService = tutorService;
		this.mascotaService = mascotaService;
		this.turnoService = turnoService;
		this.tutorRepository = tutorRepository;
	}

	@Override
	@Transactional
	public TurnoClinicoResponseDTO agendar(TurnoConAltaRequestDTO request) {
		Long mascotaId = resolverMascota(request.tutorId(), request.tutorNuevo(), request.mascotaId(),
				request.mascotaNueva());
		return turnoService.crear(new TurnoClinicoRequestDTO(
				request.fechaHoraInicio(), request.motivo(), mascotaId, request.profesionalId(), null));
	}

	@Override
	@Transactional
	public TurnoClinicoResponseDTO completarDatos(Long turnoId, CompletarDatosRequestDTO request) {
		// Antes de crear cliente o mascota: un turno que ya tiene datos no deja altas sueltas.
		turnoService.verificarPendienteDeDatos(turnoId);
		Long mascotaId = resolverMascota(request.tutorId(), request.tutorNuevo(), request.mascotaId(),
				request.mascotaNueva());
		TurnoClinicoResponseDTO turno = turnoService.asignarMascotaDeUrgencia(turnoId, mascotaId);
		consultaService.asignarMascotaDeUrgencia(turnoId, mascotaId);
		return turno;
	}

	/**
	 * Devuelve la mascota del pedido, creando cliente y/o mascota si hacen falta.
	 * Con cliente y mascota existentes, la mascota tiene que ser de ese cliente.
	 */
	private Long resolverMascota(Long tutorId, TutorRequestDTO tutorNuevo, Long mascotaId,
			MascotaRequestDTO mascotaNueva) {
		validarCombinacion(tutorId, tutorNuevo, mascotaId, mascotaNueva);
		if (mascotaId != null && tutorId != null) {
			// El cliente indicado tiene que ser uno de los tutores de la mascota: si no,
			// el pedido trae un dato inconsistente y se rechaza en vez de ignorarlo.
			tutorExistente(tutorId);
			if (vinculoRepository.findByTutorIdAndMascotaId(tutorId, mascotaId).isEmpty()) {
				throw new BadRequestException("La mascota elegida no pertenece al cliente indicado");
			}
		}
		if (mascotaNueva != null) {
			Long titular = tutorNuevo != null ? tutorService.crear(tutorNuevo).id() : tutorExistente(tutorId);
			return mascotaService.crear(conTitular(mascotaNueva, titular)).id();
		}
		return mascotaId;
	}

	/** Se valida antes de escribir nada, para que un 400 de combinacion no dependa del rollback. */
	private static void validarCombinacion(Long tutorId, TutorRequestDTO tutorNuevo, Long mascotaId,
			MascotaRequestDTO mascotaNueva) {
		boolean mascotaExistente = mascotaId != null;
		boolean esMascotaNueva = mascotaNueva != null;
		boolean tutorExistente = tutorId != null;
		boolean esTutorNuevo = tutorNuevo != null;

		if (mascotaExistente && esMascotaNueva) {
			throw new BadRequestException("Indicá una mascota existente o una nueva, no las dos");
		}
		if (tutorExistente && esTutorNuevo) {
			throw new BadRequestException("Indicá un cliente existente o uno nuevo, no los dos");
		}
		if (!mascotaExistente && !esMascotaNueva) {
			throw new BadRequestException("Falta la mascota del turno");
		}
		if (esTutorNuevo && !esMascotaNueva) {
			throw new BadRequestException("Un cliente nuevo se carga con una mascota nueva");
		}
		if (esMascotaNueva && !tutorExistente && !esTutorNuevo) {
			throw new BadRequestException("La mascota nueva necesita un cliente: elegí uno existente o cargá uno nuevo");
		}
	}

	private Long tutorExistente(Long tutorId) {
		Tutor tutor = tutorRepository.findById(tutorId)
				.orElseThrow(() -> ResourceNotFoundException.tutor(tutorId));
		if (!tutor.isActivo()) {
			throw new BadRequestException("El cliente " + tutor.getNombreCompleto() + " esta desactivado");
		}
		return tutorId;
	}

	/** La mascota nueva queda con su cliente como titular, autorizado a retirarla. */
	private static MascotaRequestDTO conTitular(MascotaRequestDTO mascota, Long tutorId) {
		return new MascotaRequestDTO(mascota.nombre(), mascota.especie(), mascota.raza(), mascota.sexo(),
				mascota.fechaNacimiento(), mascota.pesoKg(), mascota.conducta(), mascota.chip(),
				mascota.observaciones(), List.of(new MascotaRequestDTO.VinculoTutorDTO(tutorId, true)));
	}
}
