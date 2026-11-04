package com.veterinaria.veterinaria_backend.dto;

import jakarta.validation.constraints.NotNull;

/** Mascota que ocupa la plaza de urgencia del dia. */
public record OcuparPlazaUrgenciaRequestDTO(
		@NotNull(message = "El id de la mascota es obligatorio") Long mascotaId) {
}
