package com.veterinaria.veterinaria_backend.service.impl;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.ProfesionalRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ProfesionalResponseDTO;
import com.veterinaria.veterinaria_backend.entity.Profesional;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.mapper.EntityMapper;
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

	/** Recorta y pasa a mayusculas. Un DNI o matricula en blanco se trata como null. */
	private String normalizar(String valor) {
		if (valor == null) {
			return null;
		}
		String recortado = valor.trim().toUpperCase(Locale.ROOT);
		return recortado.isEmpty() ? null : recortado;
	}

	/**
	 * La matricula se compara sin espacios internos ("MAT 01" y "MAT01" son la
	 * misma), pero se guarda con ellos: la matricula se muestra en la ficha
	 * clinica y se lee en voz alta, y "MAT-01" queda mejor que "MAT01".
	 */
	private String normalizarMatricula(String valor) {
		if (valor == null) {
			return null;
		}
		String recortado = valor.trim().toUpperCase(Locale.ROOT);
		if (recortado.isEmpty()) {
			return null;
		}
		return recortado.replaceAll("\\s+", " ");
	}

	/**
	 * El telefono se guarda tal cual, sin pasar por mayusculas: no tiene letras
	 * que normalizar y los prefijos con "+" se leen mejor intactos.
	 */
	private String normalizarTelefono(String valor) {
		if (valor == null) {
			return null;
		}
		String recortado = valor.trim();
		return recortado.isEmpty() ? null : recortado;
	}
}
