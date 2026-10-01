package com.veterinaria.veterinaria_backend.service.impl;

import java.time.LocalDate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.entity.ControlAforoDiario;
import com.veterinaria.veterinaria_backend.repository.ControlAforoDiarioRepository;

/**
 * Crea la fila del semaforo de aforo en su propia transaccion.
 *
 * <p>Es una bean separada y no un metodo de {@link ControlAforoService} a
 * proposito: {@code REQUIRES_NEW} solo surte efecto cuando la llamada atraviesa
 * el proxy de Spring, y una llamada interna ({@code this.metodo()}) lo esquiva
 * por completo. Con las dos clases separadas la propagacion se aplica de verdad.
 *
 * <p>La fila se commitea antes de que el llamador la bloquee. Si se creara
 * dentro de la transaccion del llamador y este hiciera rollback, el semaforo
 * desapareceria aunque las reservas ya se hubieran contado.
 *
 * <p><b>La excepcion de unicidad se deja propagar.</b> Si se atrapara aqui, la
 * transaccion {@code REQUIRES_NEW} quedaria marcada como rollback-only y el
 * commit final lanzaria {@code UnexpectedRollbackException} en vez de un
 * error de clave duplicada limpio. Que la capture el llamador, que ya esta en
 * otra transaccion, es lo unico que mantiene el resultado correcto.</p>
 */
@Service
public class ControlAforoInicializador {

	private final ControlAforoDiarioRepository controlRepository;

	public ControlAforoInicializador(ControlAforoDiarioRepository controlRepository) {
		this.controlRepository = controlRepository;
	}

	/**
	 * Inserta la fila del dia si no existe.
	 *
	 * <p>Dos peticiones simultaneas para un dia nuevo compiten por el mismo
	 * INSERT: una gana por la constraint UNIQUE y la otra recibe
	 * {@link DataIntegrityViolationException}, que el llamador interpreta como
	 * "la fila ya esta", no como "algo fallo".</p>
	 *
	 * @throws DataIntegrityViolationException si otra transaccion gano la carrera
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void asegurarFila(LocalDate fecha) {
		if (controlRepository.findByFecha(fecha).isPresent()) {
			return;
		}

		ControlAforoDiario control = new ControlAforoDiario();
		control.setFecha(fecha);
		control.setCuposOcupados(0);

		controlRepository.saveAndFlush(control);
	}
}