package com.veterinaria.veterinaria_backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.ProfesionalRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ProfesionalResponseDTO;
import com.veterinaria.veterinaria_backend.entity.Profesional;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.repository.ProfesionalRepository;
import com.veterinaria.veterinaria_backend.service.ProfesionalService;

@Service
@Transactional(readOnly = true)
public class ProfesionalServiceImpl implements ProfesionalService {

	private final ProfesionalRepository profesionalRepository;

	public ProfesionalServiceImpl(ProfesionalRepository profesionalRepository) {
		this.profesionalRepository = profesionalRepository;
	}

	@Override
	public List<ProfesionalResponseDTO> listar() {
		return profesionalRepository.findAll().stream().map(this::toResponse).toList();
	}

	@Override
	@Transactional
	public ProfesionalResponseDTO crear(ProfesionalRequestDTO request) {
		if (request.matricula() != null && !request.matricula().isBlank()
				&& profesionalRepository.findByMatricula(request.matricula()).isPresent()) {
			throw new BadRequestException("La matricula " + request.matricula() + " ya esta registrada");
		}

		Profesional profesional = new Profesional();
		profesional.setNombre(request.nombre());
		profesional.setApellido(request.apellido());
		profesional.setMatricula(request.matricula());
		profesional.setActivo(true);

		return toResponse(profesionalRepository.save(profesional));
	}

	private ProfesionalResponseDTO toResponse(Profesional profesional) {
		return new ProfesionalResponseDTO(
				profesional.getId(),
				profesional.getNombre(),
				profesional.getApellido(),
				profesional.getNombreCompleto(),
				profesional.getMatricula(),
				profesional.isActivo());
	}
}