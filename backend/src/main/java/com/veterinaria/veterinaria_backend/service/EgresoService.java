package com.veterinaria.veterinaria_backend.service;

import java.time.LocalDate;
import java.util.List;

import com.veterinaria.veterinaria_backend.dto.EgresoRegistradoResponseDTO;
import com.veterinaria.veterinaria_backend.dto.EgresoRegistroRequestDTO;
import com.veterinaria.veterinaria_backend.dto.EgresoValidacionResponseDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaDelDiaResponseDTO;

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

	/**
	 * Mascotas con actividad en la fecha, para el checkout del mostrador.
	 *
	 * <p>No es el semaforo: es la lista de candidatos. El mostrador no puede
	 * elegir a quien va a validar si primero tiene que acordarse de que el animal
	 * esta en el local. Los tutores viajan con su flag de retiro porque la lista
	 * tiene que poder distinguir de entrada a quien puede llevarselo de a quien solo
	 * vino a pagar, pero no se usa para autorizar pagos: esa es otra regla.</p>
	 *
	 * <p>Delega en {@link JornadaDelDiaService}: la caja del dia necesita la misma
	 * jornada y las dos tienen que ver exactamente los mismos animales.</p>
	 *
	 * @param fecha jornada a listar; null o vacio significa hoy
	 */
	List<MascotaDelDiaResponseDTO> listarPacientesDelDia(LocalDate fecha);

	/**
	 * Registra la entrega de una mascota. Vuelve a validar el retiro con
	 * {@link #validarPorTutorId}: si no esta permitido responde 409 con el mismo
	 * mensaje del veredicto. Finaliza la estadia de guarderia de la fecha, si hay.
	 */
	EgresoRegistradoResponseDTO registrarEntrega(EgresoRegistroRequestDTO request);
}