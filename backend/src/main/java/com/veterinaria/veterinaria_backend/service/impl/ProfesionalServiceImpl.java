package com.veterinaria.veterinaria_backend.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.ProfesionalRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ProfesionalResponseDTO;
import com.veterinaria.veterinaria_backend.entity.Profesional;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.mapper.EntityMapper;
import com.veterinaria.veterinaria_backend.normalizacion.NormalizadorAr;
import com.veterinaria.veterinaria_backend.repository.ConsultaMedicaRepository;
import com.veterinaria.veterinaria_backend.repository.ProfesionalRepository;
import com.veterinaria.veterinaria_backend.repository.TurnoClinicoRepository;
import com.veterinaria.veterinaria_backend.service.ProfesionalService;

/**
 * Implementacion transaccional de {@link ProfesionalService}.
 *
 * <p>Los dos identificadores (DNI y matricula) se normalizan con {@link #normalizar}
 * antes de comparar y antes de guardar. Sin esa normalizacion, "MAT-01", "mat-01"
 * y " MAT-01 " passarian los tres el chequeo de duplicados y se guardarian como
 * profesionales distintos con la misma matricula, que es justo lo que la
 * matricula tiene que impedir.</p>
 */
@Service
@Transactional(readOnly = true)
public class ProfesionalServiceImpl implements ProfesionalService {

	private final ProfesionalRepository profesionalRepository;
	private final EntityMapper mapper;
	private final TurnoClinicoRepository turnoRepository;
	private final ConsultaMedicaRepository consultaRepository;

	public ProfesionalServiceImpl(ProfesionalRepository profesionalRepository, EntityMapper mapper,
			TurnoClinicoRepository turnoRepository, ConsultaMedicaRepository consultaRepository) {
		this.profesionalRepository = profesionalRepository;
		this.mapper = mapper;
		this.turnoRepository = turnoRepository;
		this.consultaRepository = consultaRepository;
	}

	@Override
	public List<ProfesionalResponseDTO> listar() {
		return mapper.toProfesionalResponseList(profesionalRepository.findAll());
	}

	@Override
	@Transactional
	public ProfesionalResponseDTO crear(ProfesionalRequestDTO request) {
		String dni = normalizar(request.dni());
		String matricula = normalizarMatricula(request.matricula());

		rechazarDuplicado(profesionalRepository.findByMatricula(matricula).stream().toList(),
				matricula, "La matricula");
		rechazarDuplicado(profesionalRepository.findByDni(dni), dni, "El DNI");

		Profesional profesional = new Profesional();
		profesional.setNombre(request.nombre().trim());
		profesional.setApellido(request.apellido().trim());
		profesional.setDni(dni);
		profesional.setMatricula(matricula);
		profesional.setTelefono(normalizarTelefono(request.telefono()));
		profesional.setActivo(true);

		return mapper.toResponse(profesionalRepository.save(profesional));
	}

	@Override
	@Transactional
	public ProfesionalResponseDTO actualizar(Long id, ProfesionalRequestDTO request) {
		Profesional profesional = obtener(id);
		String dni = normalizar(request.dni());
		String matricula = normalizarMatricula(request.matricula());

		// Misma unicidad que el alta, pero sin contarse a si mismo: conservar la
		// propia matricula al editar el telefono no es un duplicado.
		rechazarDuplicado(sinElMismo(profesionalRepository.findByMatricula(matricula).stream().toList(), id),
				matricula, "La matricula");
		rechazarDuplicado(sinElMismo(profesionalRepository.findByDni(dni), id), dni, "El DNI");

		profesional.setNombre(request.nombre().trim());
		profesional.setApellido(request.apellido().trim());
		profesional.setDni(dni);
		profesional.setMatricula(matricula);
		profesional.setTelefono(normalizarTelefono(request.telefono()));
		return mapper.toResponse(profesionalRepository.save(profesional));
	}

	@Override
	@Transactional
	public void eliminar(Long id) {
		Profesional profesional = obtener(id);
		// Igual que con las mascotas: la FK impediria el borrado con un 500 opaco.
		// Se rechaza con 409 y un motivo legible.
		if (turnoRepository.existsByProfesionalId(id) || consultaRepository.existsByProfesionalId(id)) {
			throw new BusinessConflictException("No se puede eliminar a " + profesional.getNombreCompleto()
					+ ": tiene turnos o consultas registrados.");
		}
		profesionalRepository.delete(profesional);
	}

	// ------------------------------------------------------------- internos

	private Profesional obtener(Long id) {
		return profesionalRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No existe el profesional " + id));
	}

	private static List<Profesional> sinElMismo(List<Profesional> encontrados, Long id) {
		return encontrados.stream().filter(p -> !p.getId().equals(id)).toList();
	}

	/**
	 * Corta el alta si el identificador ya pertenece a otro profesional.
	 *
	 * <p>El mensaje distingue que campo fallo porque el mostrador corrige uno por
	 * vez: ver "La matricula X ya esta registrada" sobre un formulario de cinco
	 * campos es accionable; ver "Ya existe un profesional con esos datos" no.</p>
	 *
	 * @param valor     identificador ya normalizado, o null si el campo no vino
	 * @param etiqueta  como nombrar el campo en el mensaje de error
	 */
	private void rechazarDuplicado(List<Profesional> encontrados, String valor, String etiqueta) {
		if (valor == null || encontrados.isEmpty()) {
			return;
		}
		throw new BadRequestException(etiqueta + " " + valor + " ya esta registrada");
	}

	/**
	 * El DTO ya llega normalizado (ver {@link NormalizadorAr}); se vuelve a pasar
	 * por el normalizador para que el servicio no dependa de quien lo construyo.
	 * Un DNI en blanco se trata como null.
	 */
	private String normalizar(String valor) {
		return NormalizadorAr.dni(valor);
	}

	/** Matricula comparable: "m.p. 1234" y "MP1234" son la misma ("MP 1234"). */
	private String normalizarMatricula(String valor) {
		return NormalizadorAr.matricula(valor);
	}

	/** Telefono como numero nacional de 10 digitos. */
	private String normalizarTelefono(String valor) {
		return NormalizadorAr.telefono(valor);
	}
}
