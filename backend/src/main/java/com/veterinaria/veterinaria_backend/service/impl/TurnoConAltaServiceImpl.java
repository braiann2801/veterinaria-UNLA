package com.veterinaria.veterinaria_backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.MascotaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoClinicoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoClinicoResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoConAltaRequestDTO;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.repository.TutorRepository;
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

	public TurnoConAltaServiceImpl(TutorService tutorService, MascotaService mascotaService,
			TurnoClinicoService turnoService, TutorRepository tutorRepository) {
		this.tutorService = tutorService;
		this.mascotaService = mascotaService;
		this.turnoService = turnoService;
		this.tutorRepository = tutorRepository;
	}

	@Override
	@Transactional
	public TurnoClinicoResponseDTO agendar(TurnoConAltaRequestDTO request) {
		validarCombinacion(request);

		Long mascotaId = request.mascotaId();
		if (request.mascotaNueva() != null) {
			Long tutorId = request.tutorNuevo() != null
					? tutorService.crear(request.tutorNuevo()).id()
					: tutorExistente(request.tutorId());
			mascotaId = mascotaService.crear(conTitular(request.mascotaNueva(), tutorId)).id();
		}

		return turnoService.crear(new TurnoClinicoRequestDTO(
				request.fechaHoraInicio(), request.motivo(), mascotaId, request.profesionalId(), null));
	}

	/** Se valida antes de escribir nada, para que un 400 de combinacion no dependa del rollback. */
	private void validarCombinacion(TurnoConAltaRequestDTO request) {
		boolean mascotaExistente = request.mascotaId() != null;
		boolean mascotaNueva = request.mascotaNueva() != null;
		boolean tutorExistente = request.tutorId() != null;
		boolean tutorNuevo = request.tutorNuevo() != null;

		if (mascotaExistente && mascotaNueva) {
			throw new BadRequestException("Indicá una mascota existente o una nueva, no las dos");
		}
		if (tutorExistente && tutorNuevo) {
			throw new BadRequestException("Indicá un cliente existente o uno nuevo, no los dos");
		}
		if (!mascotaExistente && !mascotaNueva) {
			throw new BadRequestException("Falta la mascota del turno");
		}
		if (tutorNuevo && !mascotaNueva) {
			throw new BadRequestException("Un cliente nuevo se carga con una mascota nueva");
		}
		if (mascotaNueva && !tutorExistente && !tutorNuevo) {
			throw new BadRequestException("La mascota nueva necesita un cliente: elegí uno existente o cargá uno nuevo");
		}
	}

	private Long tutorExistente(Long tutorId) {
		if (!tutorRepository.existsById(tutorId)) {
			throw ResourceNotFoundException.tutor(tutorId);
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
