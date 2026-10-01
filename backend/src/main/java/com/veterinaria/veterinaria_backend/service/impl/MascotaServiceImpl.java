package com.veterinaria.veterinaria_backend.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.MascotaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TutorResumenDTO;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.Tutor;
import com.veterinaria.veterinaria_backend.entity.TutorMascota;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.mapper.EntityMapper;
import com.veterinaria.veterinaria_backend.repository.MascotaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorMascotaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorRepository;
import com.veterinaria.veterinaria_backend.service.MascotaService;

/**
 * Implementacion transaccional de {@link MascotaService}.
 */
@Service
@Transactional(readOnly = true)
public class MascotaServiceImpl implements MascotaService {

	private final MascotaRepository mascotaRepository;
	private final TutorRepository tutorRepository;
	private final TutorMascotaRepository tutorMascotaRepository;
	private final EntityMapper mapper;

	public MascotaServiceImpl(MascotaRepository mascotaRepository, TutorRepository tutorRepository,
			TutorMascotaRepository tutorMascotaRepository, EntityMapper mapper) {
		this.mascotaRepository = mascotaRepository;
		this.tutorRepository = tutorRepository;
		this.tutorMascotaRepository = tutorMascotaRepository;
		this.mapper = mapper;
	}

	@Override
	public List<MascotaResponseDTO> listar() {
		return mapper.toMascotaResponseList(mascotaRepository.findAll());
	}

	@Override
	public MascotaResponseDTO buscarPorId(Long id) {
		return mapper.toResponse(obtenerEntidad(id));
	}

	@Override
	@Transactional
	public MascotaResponseDTO crear(MascotaRequestDTO request) {
		if (request.chip() != null && !request.chip().isBlank()
				&& mascotaRepository.findByChip(request.chip()).isPresent()) {
			throw new BadRequestException("Ya existe una mascota con el chip " + request.chip());
		}

		Mascota mascota = new Mascota();
		mapper.applyRequest(request, mascota);
		mascota.setVinculos(new ArrayList<>());
		Mascota guardada = mascotaRepository.save(mascota);

		sincronizarTutores(guardada, request.tutores());
		return mapper.toResponse(mascotaRepository.findById(guardada.getId()).orElseThrow());
	}

	@Override
	@Transactional
	public MascotaResponseDTO actualizar(Long id, MascotaRequestDTO request) {
		Mascota mascota = obtenerEntidad(id);

		// Se resuelve fuera de la lambda: lanzar desde un Optional.ifPresent
		// mezcla la logica de negocio con la de flujo y dificulta el diagnostico.
		if (request.chip() != null && !request.chip().isBlank()) {
			Optional<Mascota> conMismoChip = mascotaRepository.findByChip(request.chip());
			if (conMismoChip.isPresent() && !Objects.equals(conMismoChip.get().getId(), id)) {
				throw new BadRequestException("El chip " + request.chip() + " ya pertenece a otra mascota");
			}
		}

		mapper.applyRequest(request, mascota);
		sincronizarTutores(mascota, request.tutores());

		return mapper.toResponse(mascota);
	}

	@Override
	@Transactional
	public void eliminar(Long id) {
		mascotaRepository.delete(obtenerEntidad(id));
	}

	@Override
	public List<TutorResumenDTO> listarTutores(Long mascotaId) {
		Mascota mascota = obtenerEntidad(mascotaId);
		return mapper.toResponse(mascota).tutores();
	}

	// ------------------------------------------------------------- internos

	private Mascota obtenerEntidad(Long id) {
		if (id == null) {
			throw new BadRequestException("El id de la mascota es obligatorio");
		}
		return mascotaRepository.findById(id)
				.orElseThrow(() -> ResourceNotFoundException.mascota(id));
	}

	private Tutor obtenerTutor(Long id) {
		return tutorRepository.findById(id)
				.orElseThrow(() -> ResourceNotFoundException.tutor(id));
	}

	/**
	 * Sincroniza los tutores declarados en el request contra los vinculos
	 * actuales de la mascota.
	 */
	private void sincronizarTutores(Mascota mascota, List<MascotaRequestDTO.VinculoTutorDTO> solicitados) {
		Map<Long, MascotaRequestDTO.VinculoTutorDTO> pedidoPorTutor = solicitados == null
				? Map.of()
				: solicitados.stream()
						.collect(Collectors.toMap(
								MascotaRequestDTO.VinculoTutorDTO::tutorId,
								Function.identity(),
								(viejo, nuevo) -> nuevo,
								LinkedHashMap::new));

		for (Long tutorId : pedidoPorTutor.keySet()) {
			obtenerTutor(tutorId);
		}

		// Borrado explicito + flush antes de recrear tuplas, para no dejar un
		// DELETE pendiente que colisione con el INSERT del mismo id compuesto.
		List<TutorMascota> aBorrar = mascota.getVinculos().stream()
				.filter(Objects::nonNull)
				.filter(vinculo -> !pedidoPorTutor.containsKey(vinculo.getTutor().getId()))
				.toList();

		for (TutorMascota vinculo : aBorrar) {
			Tutor tutor = vinculo.getTutor();
			if (tutor.getVinculos() != null) {
				tutor.getVinculos().remove(vinculo);
			}
			mascota.getVinculos().remove(vinculo);
			tutorMascotaRepository.delete(vinculo);
		}
		tutorMascotaRepository.flush();

		for (Map.Entry<Long, MascotaRequestDTO.VinculoTutorDTO> entry : pedidoPorTutor.entrySet()) {
			TutorMascota existente = mascota.getVinculos().stream()
					.filter(Objects::nonNull)
					.filter(v -> Objects.equals(v.getTutor().getId(), entry.getKey()))
					.findFirst()
					.orElse(null);

			boolean autorizado = Boolean.TRUE.equals(entry.getValue().autorizadoRetiro());

			if (existente != null) {
				existente.setAutorizadoRetiro(autorizado);
				continue;
			}

			Tutor tutor = obtenerTutor(entry.getKey());
			TutorMascota vinculo = new TutorMascota(tutor, mascota, autorizado);
			vinculo.setVinculadoDesde(LocalDateTime.now());
			mascota.getVinculos().add(vinculo);
			if (tutor.getVinculos() != null) {
				tutor.getVinculos().add(vinculo);
			}
		}
	}
}