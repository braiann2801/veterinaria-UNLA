package com.veterinaria.veterinaria_backend.mapper;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.veterinaria.veterinaria_backend.dto.MascotaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaResumenDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TutorRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TutorResumenDTO;
import com.veterinaria.veterinaria_backend.dto.TutorResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TutorMascotaResponseDTO;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.Tutor;
import com.veterinaria.veterinaria_backend.entity.TutorMascota;

/**
 * Traduccion entre entidades JPA y DTOs de la API.
 *
 * <p>Reglas que aplica este mapper:</p>
 * <ul>
 *   <li>La API nunca recibe ni devuelve entidades, siempre DTOs.</li>
 *   <li>La navegacion de la relacion bidireccional se corta en el nivel 1: las
 *       listas anidadas usan unicamente DTOs resumen, que no tienen referencias
 *       de vuelta. Por eso Jackson no puede entrar en ciclo.</li>
 *   <li>Las colecciones nulas o huerfanas se normalizan a listas vacias para no
 *       propagar nulls al frontend.</li>
 * </ul>
 */
@Component
public class EntityMapper {

	// ---------------------------------------------------------------- Tutor

	public TutorResponseDTO toResponse(Tutor tutor) {
		if (tutor == null) {
			return null;
		}
		return new TutorResponseDTO(
				tutor.getId(),
				tutor.getNombre(),
				tutor.getApellido(),
				tutor.getDni(),
				tutor.getTelefono(),
				tutor.getEmail(),
				tutor.getDireccion(),
				tutor.getNombreCompleto(),
				toMascotasVinculadas(tutor));
	}

	/**
	 * Nota: los metodos de lista llevan nombre propio por tipo en lugar de
	 * sobrecargar {@code toResponseList}, porque la erasure de {@code List} hace
	 * que las tres firmas colisionen en tiempo de compilacion.
	 */
	public List<TutorResponseDTO> toTutorResponseList(List<Tutor> tutores) {
		if (tutores == null) {
			return List.of();
		}
		return tutores.stream().filter(Objects::nonNull).map(this::toResponse).toList();
	}

	private List<TutorResponseDTO.MascotaVinculadaDTO> toMascotasVinculadas(Tutor tutor) {
		List<TutorMascota> vinculos = tutor.getVinculos();
		if (vinculos == null) {
			return List.of();
		}
		return vinculos.stream()
				.filter(Objects::nonNull)
				.filter(vinculo -> vinculo.getMascota() != null)
				.map(vinculo -> new TutorResponseDTO.MascotaVinculadaDTO(
						vinculo.getMascota().getId(),
						vinculo.getMascota().getNombre(),
						toEspecieResumen(vinculo.getMascota().getEspecie()),
						vinculo.getMascota().getRaza(),
						vinculo.isAutorizadoRetiro()))
				.toList();
	}

	/**
	 * Proyecta los datos de un {@code TutorRequestDTO} sobre una entidad existente.
	 * No toca la relacion con mascotas: esa se resuelve en la capa de servicio.
	 */
	public void applyRequest(TutorRequestDTO request, Tutor tutor) {
		if (request == null || tutor == null) {
			return;
		}
		tutor.setNombre(request.nombre());
		tutor.setApellido(request.apellido());
		tutor.setDni(request.dni());
		tutor.setTelefono(request.telefono());
		tutor.setEmail(request.email());
		tutor.setDireccion(request.direccion());
	}

	// ------------------------------------------------------------- Mascota

	public MascotaResponseDTO toResponse(Mascota mascota) {
		if (mascota == null) {
			return null;
		}
		return new MascotaResponseDTO(
				mascota.getId(),
				mascota.getNombre(),
				mascota.getEspecie() != null ? mascota.getEspecie().name() : null,
				mascota.getRaza(),
				mascota.getSexo(),
				mascota.getFechaNacimiento(),
				mascota.getPesoKg(),
				conductaToApi(mascota.getConducta()),
				mascota.getChip(),
				mascota.getObservaciones(),
				toTutoresResumen(mascota));
	}

	public List<MascotaResponseDTO> toMascotaResponseList(List<Mascota> mascotas) {
		if (mascotas == null) {
			return List.of();
		}
		return mascotas.stream().filter(Objects::nonNull).map(this::toResponse).toList();
	}

	private List<TutorResumenDTO> toTutoresResumen(Mascota mascota) {
		List<TutorMascota> vinculos = mascota.getVinculos();
		if (vinculos == null) {
			return List.of();
		}
		return vinculos.stream()
				.filter(Objects::nonNull)
				.filter(vinculo -> vinculo.getTutor() != null)
				.map(vinculo -> toResumen(vinculo.getTutor(), vinculo.isAutorizadoRetiro()))
				.toList();
	}

	private TutorResumenDTO toResumen(Tutor tutor, boolean autorizadoRetiro) {
		return new TutorResumenDTO(
				tutor.getId(),
				tutor.getNombre(),
				tutor.getApellido(),
				tutor.getTelefono(),
				autorizadoRetiro);
	}

	/**
	 * Proyecta los datos de un {@code MascotaRequestDTO} sobre una entidad existente.
	 * No crea ni borra vinculos: esa es responsabilidad del servicio.
	 */
	public void applyRequest(MascotaRequestDTO request, Mascota mascota) {
		if (request == null || mascota == null) {
			return;
		}
		mascota.setNombre(request.nombre());
		mascota.setEspecie(request.especie() != null
				? Mascota.Especie.valueOf(request.especie().name())
				: null);
		mascota.setRaza(request.raza());
		mascota.setSexo(request.sexo());
		mascota.setFechaNacimiento(request.fechaNacimiento());
		mascota.setPesoKg(request.pesoKg());
		mascota.setConducta(toConducta(request.conducta()));
		mascota.setChip(request.chip());
		mascota.setObservaciones(request.observaciones());
	}

	/**
	 * Traduce la conducta de la API (con ñ) al valor ASCII persistido.
	 * El enum JPA usa GRUNE a proposito, ver {@link Mascota.Conducta}.
	 */
	public static String conductaToApi(Mascota.Conducta conducta) {
		if (conducta == null) {
			return null;
		}
		return switch (conducta) {
			case GRUNE -> "gruñe";
			case NORMAL -> "normal";
			case MUERDE -> "muerde";
			case MIEDOSO -> "miedoso";
		};
	}

	private Mascota.Conducta toConducta(String conducta) {
		if (conducta == null || conducta.isBlank()) {
			return null;
		}
		String normalizado = conducta.trim().toUpperCase();
		if ("GRUÑE".equals(normalizado) || "GRUÑE".equalsIgnoreCase(conducta.trim())) {
			return Mascota.Conducta.GRUNE;
		}
		return Mascota.Conducta.valueOf(normalizado);
	}

	private MascotaResumenDTO.Especie toEspecieResumen(Mascota.Especie especie) {
		if (especie == null) {
			return null;
		}
		return MascotaResumenDTO.Especie.valueOf(especie.name());
	}

	// -------------------------------------------------------- TutorMascota

	public TutorMascotaResponseDTO toResponse(TutorMascota vinculo) {
		if (vinculo == null) {
			return null;
		}
		return new TutorMascotaResponseDTO(
				vinculo.getId() != null ? vinculo.getId().getTutorId() : null,
				vinculo.getId() != null ? vinculo.getId().getMascotaId() : null,
				vinculo.isAutorizadoRetiro(),
				vinculo.getVinculadoDesde());
	}

	public List<TutorMascotaResponseDTO> toVinculoResponseList(List<TutorMascota> vinculos) {
		if (vinculos == null) {
			return List.of();
		}
		return vinculos.stream().filter(Objects::nonNull).map(this::toResponse).toList();
	}

	/**
	 * Construye una entidad de vinculo sin persistirla. El servicio debe validar
	 * que ambos extremos existan antes de guardar.
	 */
	public TutorMascota toEntity(Tutor tutor, Mascota mascota, boolean autorizadoRetiro) {
		TutorMascota vinculo = new TutorMascota(tutor, mascota, autorizadoRetiro);
		// id sera poblado por Hibernate gracias a @MapsId al persistir.
		return vinculo;
	}
}