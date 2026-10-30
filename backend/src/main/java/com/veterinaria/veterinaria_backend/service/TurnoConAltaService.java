package com.veterinaria.veterinaria_backend.service;

import com.veterinaria.veterinaria_backend.dto.TurnoClinicoResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoConAltaRequestDTO;

/** Alta de turno que en el mismo paso puede cargar el cliente y la mascota. */
public interface TurnoConAltaService {

	/**
	 * Crea lo que haga falta (cliente, mascota) y agenda el turno en una sola
	 * transaccion: si el turno choca (409) o algo es invalido, no queda nada creado.
	 */
	TurnoClinicoResponseDTO agendar(TurnoConAltaRequestDTO request);
}
