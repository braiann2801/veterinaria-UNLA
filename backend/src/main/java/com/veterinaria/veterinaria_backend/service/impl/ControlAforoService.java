package com.veterinaria.veterinaria_backend.service.impl;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.entity.ControlAforoDiario;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.repository.ControlAforoDiarioRepository;

/**
 * Gestion del semaforo de aforo por dia.
 *
 * <p>Su unica responsabilidad es devolver la fila del dia con el lock de
 * escritura tomado. La creacion la delega en {@link ControlAforoInicializador},
 * que corre en {@code REQUIRES_NEW} para que el commit de la fila no dependa
 * del rollback de la transaccion que agenda la reserva.</p>
 */
@Service
public class ControlAforoService {

	private static final Logger log = LoggerFactory.getLogger(ControlAforoService.class);

	private final ControlAforoDiarioRepository controlRepository;
	private final ControlAforoInicializador inicializador;

	public ControlAforoService(ControlAforoDiarioRepository controlRepository,
			ControlAforoInicializador inicializador) {
		this.controlRepository = controlRepository;
		this.inicializador = inicializador;
	}

	/**
	 * Devuelve la fila del dia con {@code SELECT ... FOR UPDATE} aplicado.
	 *
	 * <p>El lock se mantiene hasta el commit de la transaccion del llamador,
	 * que es lo que serializa las reservas concurrentes: la segunda espera a
	 * que la primera confirme y recien entonces lee el conteo, ya actualizado.
	 * La segunda lectura puede seguir bloqueandose si otra transaccion se
	 * adelanta, y eso es correcto.</p>
	 *
	 * @throws IllegalStateException si la fila no aparece tras inicializarla
	 * @throws BusinessConflictException si se agota la espera por el lock
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public ControlAforoDiario bloquearDia(LocalDate fecha) {
		inicializarSemaforo(fecha);

		try {
			return controlRepository.bloquearPorFecha(fecha)
					.orElseThrow(() -> new IllegalStateException(
							"No se pudo bloquear el semaforo de aforo para el " + fecha));
		} catch (CannotAcquireLockException ex) {
			// Se agoto innodb_lock_wait_timeout esperando el FOR UPDATE. Se
			// traduce a 409 con mensaje de reintento.
			//
			// No queda ningun lock huerfano por nuestra parte: en InnoDB los
			// bloqueos de fila se liberan al confirmar o revertir la transaccion
			// que los tomo, nunca cuando expira la espera. El que retiene el
			// semaforo es otra transaccion viva, que lo soltara al terminar.
			//
			// Se registra el motivo original para que el diagnosable vea si fue
			// timeout o deadlock, sin exponerlo al cliente.
			log.warn("No se pudo tomar el semaforo de aforo del {}: {}", fecha, ex.getMessage());
			throw BusinessConflictException.semaforoOcupado(fecha);
		}
	}

	/**
	 * Garantiza que exista el semaforo del dia.
	 *
	 * <p>Si el INSERT pierde la carrera contra otra transaccion, la violacion
	 * de unicidad se descarta: significa que la fila ya existe, que es
	 * exactamente lo que se buscaba. La excepcion viene de una transaccion
	 * independiente que ya hizo rollback, asi que no ensucia la de aqui.</p>
	 */
	private void inicializarSemaforo(LocalDate fecha) {
		try {
			inicializador.asegurarFila(fecha);
		} catch (DataIntegrityViolationException yaLaCreoOtro) {
			// Carrera perdida: el semaforo ya existe. Es el resultado esperado.
		}
	}
}