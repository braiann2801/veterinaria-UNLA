package com.veterinaria.veterinaria_backend.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.MascotaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TutorRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TutorResponseDTO;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.Tutor;
import com.veterinaria.veterinaria_backend.entity.TutorMascota;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.mapper.EntityMapper;
import com.veterinaria.veterinaria_backend.repository.MascotaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorMascotaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorRepository;
import com.veterinaria.veterinaria_backend.service.TutorService;

/**
 * Implementacion transaccional de {@link TutorService}.
 *
 * <p>La anotacion {@code @Transactional} es obligatoria aqui: con
 * {@code open-in-view=false} la sesion se cierra al salir del metodo, y tanto la
 * navegacion de {@code tutor.getVinculos()} como el mapeo a DTO ocurren despues.</p>
 */
@Service
@Transactional(readOnly = true)
public class TutorServiceImpl implements TutorService {

	private final TutorRepository tutorRepository;
	private final MascotaRepository mascotaRepository;
	private final TutorMascotaRepository tutorMascotaRepository;
	private final EntityMapper mapper;

	public TutorServiceImpl(TutorRepository tutorRepository, MascotaRepository mascotaRepository,
			TutorMascotaRepository tutorMascotaRepository, EntityMapper mapper) {
		this.tutorRepository = tutorRepository;
		this.mascotaRepository = mascotaRepository;
		this.tutorMascotaRepository = tutorMascotaRepository;
		this.mapper = mapper;
	}

	@Override
	public List<TutorResponseDTO> listar() {
		// Las colecciones se inicializan dentro de la transaccion; el mapper las
		// proyecta a DTOs resumen antes de que se cierre la sesion.
		return mapper.toTutorResponseList(tutorRepository.findAll());
	}

	@Override
	public TutorResponseDTO buscarPorId(Long id) {
		return mapper.toResponse(obtenerEntidad(id));
	}

	@Override
	@Transactional
	public TutorResponseDTO crear(TutorRequestDTO request) {
		if (tutorRepository.existsByDni(request.dni())) {
			throw new BadRequestException("Ya existe un tutor con el DNI " + request.dni());
		}

		Tutor tutor = new Tutor();
		mapper.applyRequest(request, tutor);
		tutor.setVinculos(new ArrayList<>());
		Tutor guardado = tutorRepository.save(tutor);

		sincronizarVinculos(guardado, request.vinculos());
		return mapper.toResponse(tutorRepository.findById(guardado.getId()).orElseThrow());
	}

	@Override
	@Transactional
	public TutorResponseDTO actualizar(Long id, TutorRequestDTO request) {
		Tutor tutor = obtenerEntidad(id);

		if (!Objects.equals(tutor.getDni(), request.dni())
				&& tutorRepository.existsByDni(request.dni())) {
			throw new BadRequestException("El DNI " + request.dni() + " ya pertenece a otro tutor");
		}

		mapper.applyRequest(request, tutor);
		sincronizarVinculos(tutor, request.vinculos());

		// No se llama a save(): la entidad ya esta gestionada y el dirty
		// checking la persiste al cerrar la transaccion. Un save() aqui
		// ejecutaria merge(), que con CascadeType.ALL y id compuesto asignado
		// clona la tupla TutorMascota y lanza NonUniqueObjectException.
		return mapper.toResponse(tutor);
	}

	@Override
	@Transactional
	public void eliminar(Long id) {
		Tutor tutor = obtenerEntidad(id);
		tutorRepository.delete(tutor);
	}

	@Override
	public List<MascotaResponseDTO> listarMascotas(Long tutorId) {
		Tutor tutor = obtenerEntidad(tutorId);
		return tutor.getVinculos().stream()
				.filter(Objects::nonNull)
				.map(TutorMascota::getMascota)
				.filter(Objects::nonNull)
				.map(mapper::toResponse)
				.toList();
	}

	@Override
	@Transactional
	public MascotaResponseDTO vincularMascota(Long tutorId, Long mascotaId, boolean autorizadoRetiro) {
		Tutor tutor = obtenerEntidad(tutorId);
		Mascota mascota = obtenerMascota(mascotaId);

		boolean yaVinculada = tutor.getVinculos().stream()
				.anyMatch(vinculo -> Objects.equals(vinculo.getMascota().getId(), mascotaId));
		if (yaVinculada) {
			throw new BadRequestException(
					"La mascota " + mascotaId + " ya esta vinculada al tutor " + tutorId);
		}

		TutorMascota vinculo = new TutorMascota(tutor, mascota, autorizadoRetiro);
		vinculo.setVinculadoDesde(LocalDateTime.now());

		// La tupla se persiste en cascada al cerrar la transaccion. No hace falta
		// save() sobre el tutor: ya esta gestionado y un merge() clonaria la tupla.
		tutor.getVinculos().add(vinculo);
		mascota.getVinculos().add(vinculo);

		return mapper.toResponse(mascota);
	}

	@Override
	@Transactional
	public void desvincularMascota(Long tutorId, Long mascotaId) {
		Tutor tutor = obtenerEntidad(tutorId);

		TutorMascota vinculo = tutor.getVinculos().stream()
				.filter(Objects::nonNull)
				.filter(v -> Objects.equals(v.getMascota().getId(), mascotaId))
				.findFirst()
				.orElseThrow(() -> new ResourceNotFoundException(
						"La mascota " + mascotaId + " no esta vinculada al tutor " + tutorId));

		Mascota mascota = vinculo.getMascota();
		tutor.getVinculos().remove(vinculo);
		if (mascota.getVinculos() != null) {
			mascota.getVinculos().remove(vinculo);
		}

		tutorMascotaRepository.delete(vinculo);
	}

	// ------------------------------------------------------------- internos

	private Tutor obtenerEntidad(Long id) {
		if (id == null) {
			throw new BadRequestException("El id del tutor es obligatorio");
		}
		return tutorRepository.findById(id)
				.orElseThrow(() -> ResourceNotFoundException.tutor(id));
	}

	private Mascota obtenerMascota(Long id) {
		if (id == null) {
			throw new BadRequestException("El id de la mascota es obligatorio");
		}
		return mascotaRepository.findById(id)
				.orElseThrow(() -> ResourceNotFoundException.mascota(id));
	}

	/**
	 * Sincroniza los vinculos declarados en el request: agrega los nuevos,
	 * quita los que desaparecieron y actualiza el flag de retiro de los que se
	 * mantienen. No lanza si el vinculo ya venia con el mismo flag.
	 */
	private void sincronizarVinculos(Tutor tutor, List<TutorRequestDTO.VinculoMascotaDTO> solicitados) {
		Map<Long, TutorRequestDTO.VinculoMascotaDTO> pedidoPorMascota = solicitados == null
				? Map.of()
				: solicitados.stream()
						.collect(Collectors.toMap(
								TutorRequestDTO.VinculoMascotaDTO::mascotaId,
								Function.identity(),
								(viejo, nuevo) -> nuevo,
								LinkedHashMap::new));

		// Mascotas solicitadas: se valida existencia antes de tocar la coleccion.
		for (Long mascotaId : pedidoPorMascota.keySet()) {
			obtenerMascota(mascotaId);
		}

		// Bajas: vinculos que el tutor tiene y el request ya no menciona.
		// Se borran explicitamente por repositorio en lugar de depender de
		// orphanRemoval: si el flush pendiente no occurriera antes de recrear
		// una tupla con el mismo id, Hibernate lanzaria DuplicateKeyException.
		List<TutorMascota> aBorrar = tutor.getVinculos().stream()
				.filter(Objects::nonNull)
				.filter(vinculo -> !pedidoPorMascota.containsKey(vinculo.getMascota().getId()))
				.toList();

		for (TutorMascota vinculo : aBorrar) {
			Mascota mascota = vinculo.getMascota();
			if (mascota.getVinculos() != null) {
				mascota.getVinculos().remove(vinculo);
			}
			tutor.getVinculos().remove(vinculo);
			tutorMascotaRepository.delete(vinculo);
		}
		tutorMascotaRepository.flush();

		// Altas y actualizacion de flags.
		for (Map.Entry<Long, TutorRequestDTO.VinculoMascotaDTO> entry : pedidoPorMascota.entrySet()) {
			TutorMascota existente = tutor.getVinculos().stream()
					.filter(Objects::nonNull)
					.filter(v -> Objects.equals(v.getMascota().getId(), entry.getKey()))
					.findFirst()
					.orElse(null);

			boolean autorizado = Boolean.TRUE.equals(entry.getValue().autorizadoRetiro());

			if (existente != null) {
				existente.setAutorizadoRetiro(autorizado);
				continue;
			}

			Mascota mascota = obtenerMascota(entry.getKey());
			TutorMascota vinculo = new TutorMascota(tutor, mascota, autorizado);
			vinculo.setVinculadoDesde(LocalDateTime.now());
			tutor.getVinculos().add(vinculo);
			if (mascota.getVinculos() != null) {
				mascota.getVinculos().add(vinculo);
			}
		}
	}
}