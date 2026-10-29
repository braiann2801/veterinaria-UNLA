package com.veterinaria.veterinaria_backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Pedido de registrar una entrega. Solo trae quien y que: el backend vuelve a
 * validar autorizacion y deuda, no confia en el veredicto que mostro la pantalla.
 *
 * @param fecha jornada del checkout; sin ella, hoy
 */
public record EgresoRegistroRequestDTO(

		@NotNull(message = "El id de la mascota es obligatorio")
		Long mascotaId,

		@NotNull(message = "El id del tutor que retira es obligatorio")
		Long tutorId,

		LocalDate fecha) {
}
