package com.veterinaria.veterinaria_backend.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.TurnoClinicoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoClinicoResponseDTO;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.Profesional;
import com.veterinaria.veterinaria_backend.entity.TurnoClinico;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.exception.SolapamientoTurnoMessages;
import com.veterinaria.veterinaria_backend.repository.MascotaRepository;
import com.veterinaria.veterinaria_backend.repository.ProfesionalRepository;
import com.veterinaria.veterinaria_backend.repository.TurnoClinicoRepository;
import com.veterinaria.veterinaria_backend.service.TurnoClinicoService;

/**
 * Implementacion transaccional de {@link TurnoClinicoService}.
 *
 * <p>La validacion de solapamiento compara el bloque completo
 * ({@code fechaHoraInicio} a {@code fechaFinBloque}), no solo los 45 minutos de
 * atencion. Asi un turno que empieza cuando el anterior termina la consulta
 * tambien se rechaza: en esa franja el consultorio esta en desinfeccion.</p>
 */
@Service
@Transactional(readOnly = true)
public class TurnoClinicoServiceImpl implements TurnoClinicoService {

	private final TurnoClinicoRepository turnoRepository;
	private final MascotaRepository mascotaRepository;
	private final ProfesionalRepository profesionalRepository;

	public TurnoClinicoServiceImpl(TurnoClinicoRepository turnoRepository,
			MascotaRepository mascotaRepository, ProfesionalRepository profesionalRepository) {
		this.turnoRepository = turnoRepository;
		this.mascotaRepository = mascotaRepository;
		this.profesionalRepository = profesionalRepository;
	}

	@Override
	public List<TurnoClinicoResponseDTO> listarPorFecha(LocalDate fecha) {
		LocalDate dia = fecha != null ? fecha : LocalDate.now();
		return turnoRepository.findByFechaHoraInicioBetween(inicioDelDia(dia), finDelDia(dia)).stream()
				.map(this::toResponse)
				.toList();
	}

	@Override
	public List<TurnoClinicoResponseDTO> listarPorProfesional(Long profesionalId, LocalDate fecha) {
		LocalDate dia = fecha != null ? fecha : LocalDate.now();
		return turnoRepository
				.findByProfesionalIdAndFechaHoraInicioBetweenOrderByFechaHoraInicio(
						profesionalId, inicioDelDia(dia), finDelDia(dia))
				.stream()
				.map(this::toResponse)
				.toList();
	}

	@Override
	@Transactional
	public TurnoClinicoResponseDTO crear(TurnoClinicoRequestDTO request) {
		Mascota mascota = obtenerMascota(request.mascotaId());
		Profesional profesional = obtenerProfesional(request.profesionalId());

		TurnoClinico turno = new TurnoClinico();
		turno.setMascota(mascota);
		turno.setProfesional(profesional);
		turno.setMotivo(request.motivo());
		turno.setEstado(estadoPorDefecto(request));

		// setFechaHoraInicio dispara recalcularVentana(): +45 y +60 derivados.
		turno.setFechaHoraInicio(request.fechaHoraInicio());

		verificarSolapamiento(profesional, turno, null);

		return toResponse(turnoRepository.save(turno));
	}

	@Override
	@Transactional
	public TurnoClinicoResponseDTO actualizar(Long id, TurnoClinicoRequestDTO request) {
		TurnoClinico turno = obtenerTurno(id);

		Profesional profesional = request.profesionalId() != null
				? obtenerProfesional(request.profesionalId())
				: turno.getProfesional();

		turno.setProfesional(profesional);
		if (request.mascotaId() != null) {
			turno.setMascota(obtenerMascota(request.mascotaId()));
		}
		turno.setMotivo(request.motivo());
		if (request.estado() != null) {
			turno.setEstado(TurnoClinico.EstadoTurno.valueOf(request.estado().name()));
		}
		turno.setFechaHoraInicio(request.fechaHoraInicio());

		// El propio turno no debe chocar consigo mismo al reprogramarse.
		verificarSolapamiento(profesional, turno, turno.getId());

		return toResponse(turno);
	}

	@Override
	@Transactional
	public TurnoClinicoResponseDTO cancelar(Long id) {
		TurnoClinico turno = obtenerTurno(id);
		if (turno.getEstado() == TurnoClinico.EstadoTurno.CANCELADO) {
			throw new BadRequestException("El turno " + id + " ya estaba cancelado");
		}

		turno.setEstado(TurnoClinico.EstadoTurno.CANCELADO);
		return toResponse(turno);
	}

	@Override
	@Transactional
	public void eliminar(Long id) {
		turnoRepository.delete(obtenerTurno(id));
	}

	@Override
	public boolean disponible(Long profesionalId, LocalDateTime inicio) {
		if (profesionalId == null || inicio == null) {
			return false;
		}
		LocalDateTime finBloque = inicio.plusMinutes(
				TurnoClinico.DURACION_ATENCION_MINUTOS + TurnoClinico.DESINFECCION_MINUTOS);
		return turnoRepository.findSolapamientos(profesionalId, inicio, finBloque).isEmpty();
	}

	// ------------------------------------------------------------- internos

	/**
	 * Rechaza el turno si su bloque choca con otro del mismo profesional.
	 * Los CANCELADOS se ignoran en la consulta: no ocupan consultorio.
	 */
	private void verificarSolapamiento(Profesional profesional, TurnoClinico turno, Long excluirId) {
		List<TurnoClinico> conflictos = turnoRepository.findSolapamientos(
				profesional.getId(), turno.getFechaHoraInicio(), turno.getFechaFinBloque());

		for (TurnoClinico conflicto : conflictos) {
			if (Objects.equals(conflicto.getId(), excluirId)) {
				continue;
			}
			throw new BusinessConflictException(SolapamientoTurnoMessages.conflicto(
					profesional.getNombreCompleto(),
					conflicto.getFechaHoraInicio(),
					conflicto.getFechaHoraFin(),
					conflicto.getFechaFinBloque(),
					turno.getFechaHoraInicio(),
					turno.getFechaFinBloque()));
		}
	}

	private TurnoClinico.EstadoTurno estadoPorDefecto(TurnoClinicoRequestDTO request) {
		return request.estado() != null
				? TurnoClinico.EstadoTurno.valueOf(request.estado().name())
				: TurnoClinico.EstadoTurno.PENDIENTE;
	}

	private TurnoClinicoResponseDTO toResponse(TurnoClinico turno) {
		Mascota mascota = turno.getMascota();
		Profesional profesional = turno.getProfesional();

		// Proyeccion plana: ni mascota ni profesional arrastran sus colecciones.
		TurnoClinicoResponseDTO.MascotaTurnoResumenDTO mascotaDto = mascota == null ? null
				: new TurnoClinicoResponseDTO.MascotaTurnoResumenDTO(mascota.getId(), mascota.getNombre());

		TurnoClinicoResponseDTO.ProfesionalResumenDTO profesionalDto = profesional == null ? null
				: new TurnoClinicoResponseDTO.ProfesionalResumenDTO(
						profesional.getId(), profesional.getNombreCompleto(), profesional.getMatricula());

		return new TurnoClinicoResponseDTO(
				turno.getId(),
				turno.getFechaHoraInicio(),
				turno.getFechaHoraFin(),
				turno.getFechaFinBloque(),
				turno.getMotivo(),
				turno.getEstado() != null ? turno.getEstado().name() : null,
				mascotaDto,
				profesionalDto);
	}

	private LocalDateTime inicioDelDia(LocalDate dia) {
		return dia.atStartOfDay();
	}

	private LocalDateTime finDelDia(LocalDate dia) {
		return dia.plusDays(1).atStartOfDay();
	}

	private TurnoClinico obtenerTurno(Long id) {
		if (id == null) {
			throw new BadRequestException("El id del turno es obligatorio");
		}
		return turnoRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No existe un turno con id " + id));
	}

	private Mascota obtenerMascota(Long id) {
		if (id == null) {
			throw new BadRequestException("El id de la mascota es obligatorio");
		}
		return mascotaRepository.findById(id)
				.orElseThrow(() -> ResourceNotFoundException.mascota(id));
	}

	private Profesional obtenerProfesional(Long id) {
		if (id == null) {
			throw new BadRequestException("El id del profesional es obligatorio");
		}
		return profesionalRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No existe un profesional con id " + id));
	}
}