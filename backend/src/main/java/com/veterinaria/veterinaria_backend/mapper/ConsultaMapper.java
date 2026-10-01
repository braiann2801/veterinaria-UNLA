package com.veterinaria.veterinaria_backend.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.veterinaria.veterinaria_backend.dto.ConsultaMedicaResponseDTO;
import com.veterinaria.veterinaria_backend.entity.ConsultaMedica;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.Profesional;

/**
 * Proyeccion de la ficha clinica a su DTO.
 *
 * <p><b>Este mapper no importa ni una sola clase de caja.</b> No hay
 * {@code ComandaCobro}, ni {@code Pago}, ni un BigDecimal. La unica sombra del
 * cobro es el {@code comandaId}, que es una llave y no un dato: el frontend
 * puede saltarse a {@code /api/v1/comandas/{id}} sin que la ficha exponga
 * saldo, medio de pago ni monto.</p>
 */
@Component
public class ConsultaMapper {

	public ConsultaMedicaResponseDTO toResponse(ConsultaMedica consulta, Long comandaId) {
		Mascota mascota = consulta.getMascota();
		Profesional profesional = consulta.getProfesional();

		ConsultaMedicaResponseDTO.MascotaFichaResumenDTO mascotaDto = mascota == null ? null
				: new ConsultaMedicaResponseDTO.MascotaFichaResumenDTO(
						mascota.getId(), mascota.getNombre(),
						mascota.getEspecie() != null ? mascota.getEspecie().name() : null);

		ConsultaMedicaResponseDTO.ProfesionalFichaResumenDTO profesionalDto = profesional == null ? null
				: new ConsultaMedicaResponseDTO.ProfesionalFichaResumenDTO(
						profesional.getId(), profesional.getNombreCompleto(), profesional.getMatricula());

		return new ConsultaMedicaResponseDTO(
				consulta.getId(),
				consulta.getTurnoClinico() != null ? consulta.getTurnoClinico().getId() : null,
				consulta.getFechaAtencion(),
				consulta.getAnamnesis(),
				consulta.getDiagnostico(),
				consulta.getTratamiento(),
				consulta.getConductaObservada() != null ? consulta.getConductaObservada().name() : null,
				consulta.isAlertaConducta(),
				mascotaDto,
				profesionalDto,
				comandaId);
	}

	public List<ConsultaMedicaResponseDTO> toResponseList(List<ConsultaMedica> consultas) {
		if (consultas == null) {
			return List.of();
		}
		return consultas.stream()
				.map(consulta -> toResponse(consulta, null))
				.toList();
	}
}