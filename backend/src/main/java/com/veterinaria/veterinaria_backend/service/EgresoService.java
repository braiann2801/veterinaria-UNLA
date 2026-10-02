package com.veterinaria.veterinaria_backend.service;

import com.veterinaria.veterinaria_backend.dto.EgresoValidacionResponseDTO;

/**
 * Circuito de egreso seguro.
 *
 * <p>Una sola consulta decide si una mascota se entrega. No es un reporte: es el
 * control de acceso del mostrador, y por eso responde siempre con veredicto.</p>
 */
public interface EgresoService {

	/**
	 * Valida un retiro por DNI del tutor.
	 *
	 * @param mascotId mascota que se quiere retirar
	 * @param tutorDni documento del tutor que se presenta
	 * @return veredicto consolidado, con el motivo si esta bloqueado
	 */
	EgresoValidacionResponseDTO validarPorDni(Long mascotId, String tutorDni);

	/**
	 * Valida un retiro por id del tutor.
	 *
	 * @param mascotId mascota que se quiere retirar
	 * @param tutorId  tutor que se presenta
	 * @return veredicto consolidado, con el motivo si esta bloqueado
	 */
	EgresoValidacionResponseDTO validarPorTutorId(Long mascotId, Long tutorId);
}