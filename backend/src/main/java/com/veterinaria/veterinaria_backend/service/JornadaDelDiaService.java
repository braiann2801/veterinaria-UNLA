package com.veterinaria.veterinaria_backend.service;

import java.time.LocalDate;
import java.util.List;

import com.veterinaria.veterinaria_backend.dto.MascotaDelDiaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TutorDelDiaResponseDTO;

/**
 * Consulta de la jornada operativa: que mascotas entraron en una fecha y que
 * tutores vinieron a buscarlas o a pagar por ellas.
 *
 * <p><b>Existe para que haya una sola definicion de "actividad del dia".</b> El
 * checkout de egreso y la caja del dia necesitan la misma pregunta y, si cada uno
 * escribiera su propia version, el dia que una de las dos se olvide de excluir los
 * CANCELADOS el mostrador ve a alguien que nunca vino. Es el mismo argumento por el
 * que {@code EgresoService} delega la morosidad en
 * {@code ComandaCobroService.consultarDeuda} en vez de sumar saldos por su cuenta.</p>
 *
 * <p>Es de solo lectura y no muta nada.</p>
 */
public interface JornadaDelDiaService {

	/**
	 * Mascotas con turno clinico o reserva de guarderia en la fecha, con sus tutores
	 * vinculados.
	 *
	 * @param fecha jornada a consultar
	 * @return lista ordenada por el primer horario de actividad del dia; vacia si no
	 *         hubo movimientos
	 */
	List<MascotaDelDiaResponseDTO> mascotasDelDia(LocalDate fecha);

	/**
	 * Tutores con actividad en la fecha, agrupados y sin repetir, cada uno con las
	 * mascotas por las que estuvo.
	 *
	 * @param fecha jornada a consultar
	 */
	List<TutorDelDiaResponseDTO> tutoresDelDia(LocalDate fecha);
}
