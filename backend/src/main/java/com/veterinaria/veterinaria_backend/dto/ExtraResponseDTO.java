package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;

public record ExtraResponseDTO(Long id, String nombre, BigDecimal precio, boolean activo) {
}
