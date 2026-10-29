package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Deuda de una mascota: lo que bloquea su egreso (regla 2.5).
 *
 * <p>La deuda se ata a la mascota y no al tutor: si un tutor tiene dos
 * mascotas y salda la comanda de una, esa ya se puede retirar aunque la otra
 * siga debiendo. Las comandas sin mascota no aparecen aca; siguen siendo deuda
 * del tutor en {@link DeudaTutorResponseDTO}.</p>
 */
public record DeudaMascotaResponseDTO(
		Long mascotaId,
		String nombreMascota,
		boolean tieneDeuda,
		BigDecimal totalDeuda,
		int cantidadComandas,
		List<DeudaTutorResponseDTO.ComandaPendienteDTO> comandasPendientes) {
}
