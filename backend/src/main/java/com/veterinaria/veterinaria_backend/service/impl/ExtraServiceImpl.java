package com.veterinaria.veterinaria_backend.service.impl;

import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.ExtraRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ExtraResponseDTO;
import com.veterinaria.veterinaria_backend.entity.Extra;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.repository.ExtraRepository;
import com.veterinaria.veterinaria_backend.service.ExtraService;

@Service
@Transactional(readOnly = true)
public class ExtraServiceImpl implements ExtraService {

	private final ExtraRepository extraRepository;

	public ExtraServiceImpl(ExtraRepository extraRepository) {
		this.extraRepository = extraRepository;
	}

	@Override
	public List<ExtraResponseDTO> listar(boolean soloActivos) {
		List<Extra> extras = soloActivos
				? extraRepository.findByActivoTrueOrderByNombreAsc()
				: extraRepository.findAllByOrderByNombreAsc();
		return extras.stream().map(ExtraServiceImpl::toResponse).toList();
	}

	@Override
	@Transactional
	public ExtraResponseDTO crear(ExtraRequestDTO request) {
		rechazarNombreRepetido(request.nombre(), null);
		Extra extra = new Extra();
		aplicar(request, extra);
		extra.setActivo(request.activo() == null || request.activo());
		return toResponse(extraRepository.save(extra));
	}

	@Override
	@Transactional
	public ExtraResponseDTO actualizar(Long id, ExtraRequestDTO request) {
		Extra extra = extraRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No existe el extra " + id));
		rechazarNombreRepetido(request.nombre(), id);
		aplicar(request, extra);
		if (request.activo() != null) {
			extra.setActivo(request.activo());
		}
		return toResponse(extra);
	}

	private void rechazarNombreRepetido(String nombre, Long idPropio) {
		extraRepository.findByNombreIgnoreCase(nombre)
				.filter(existente -> !Objects.equals(existente.getId(), idPropio))
				.ifPresent(existente -> {
					throw new BadRequestException("Ya existe un extra llamado " + existente.getNombre());
				});
	}

	private static void aplicar(ExtraRequestDTO request, Extra extra) {
		extra.setNombre(request.nombre());
		extra.setPrecio(request.precio().setScale(2, RoundingMode.HALF_UP));
	}

	static ExtraResponseDTO toResponse(Extra extra) {
		return new ExtraResponseDTO(extra.getId(), extra.getNombre(), extra.getPrecio(), extra.isActivo());
	}
}
