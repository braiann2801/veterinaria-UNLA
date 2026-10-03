package com.veterinaria.veterinaria_backend.service;

import java.time.LocalDate;
import java.util.List;

import com.veterinaria.veterinaria_backend.dto.CierreCajaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.ComandaCobroResponseDTO;
import com.veterinaria.veterinaria_backend.dto.PagoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.PagoResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TutorDelDiaResponseDTO;

/**
 * Casos de uso de la caja multipago (regla 2.5).
 *
 * <p>Es la unica via para bajar el saldo de una comanda. Concentrarlo en un
 * solo servicio evita que dos puntos de entrada distintos apliquen pagos con
 * reglas diferentes y dejen la caja descuadrada.</p>
 */
public interface CajaService {

	/**
	 * Registra uno o varios pagos combinados contra una comanda.
	 *
	 * <p>Todos los pagos de la llamada se aplican o ninguno: es una sola
	 * transaccion.</p>
	 *
	 * @throws com.veterinaria.veterinaria_backend.exception.BadRequestException si la suma excede el saldo
	 * @throws com.veterinaria.veterinaria_backend.exception.BusinessConflictException si la comanda esta cancelada
	 */
	ComandaCobroResponseDTO registrarPago(Long comandaId, PagoRequestDTO request);

	/** Pagos aplicados a una comanda. */
	List<PagoResponseDTO> pagosDe(Long comandaId);

	/** Cierre de caja de una jornada, con el arqueo por metodo de pago. */
	CierreCajaResponseDTO cerrarJornada(LocalDate fecha);

	/**
	 * Tutores con actividad en la jornada: la lista de llegada de la caja.
	 *
	 * <p>Para que el mostrador elija entre quienes efectivamente vinieron y no entre
	 * las trescientas filas del padron. Sin esto, cobrar es buscar a la persona en un
	 * listado que no dice nada sobre quien estuvo hoy.</p>
	 *
	 * <p>Delega en {@link JornadaDelDiaService}: la definicion de "actividad del dia"
	 * es una sola, y el checkout de egreso necesita ver exactamente los mismos
	 * animales que la caja.</p>
	 *
	 * @param fecha jornada a listar; null significa hoy
	 */
	List<TutorDelDiaResponseDTO> tutoresDelDia(LocalDate fecha);
}