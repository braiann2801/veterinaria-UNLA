package com.veterinaria.veterinaria_backend.service;

import com.veterinaria.veterinaria_backend.dto.CompletarDatosRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoClinicoResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoConAltaRequestDTO;

/** Alta de turno que en el mismo paso puede cargar el cliente y la mascota. */
public interface TurnoConAltaService {

	/**
	 * Crea lo que haga falta (cliente, mascota) y agenda el turno en una sola
	 * transaccion: si el turno choca (409) o algo es invalido, no queda nada creado.
	 */
	TurnoClinicoResponseDTO agendar(TurnoConAltaRequestDTO request);

	/**
	 * Completa cliente y mascota de una urgencia que se registro sin datos: los
	 * asigna al turno, a su ficha (si ya se atendio) y a su comanda, que pasa a
	 * tener titular y se puede cobrar. Todo en una transaccion.
	 */
	TurnoClinicoResponseDTO completarDatos(Long turnoId, CompletarDatosRequestDTO request);
}
