package com.veterinaria.veterinaria_backend.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Fila de control (semaforo) por fecha de guarderia.
 *
 * <p><b>Por que existe.</b> El aforo de 10 (AGENTS.md 2.1) es una regla de
 * concurrencia: leer "cuantos occupied hay" y despues insertar es un
 * check-then-act que dos peticiones simultaneas pueden completar las dos. Sin
 * una fila por dia contra la que hacer {@code SELECT ... FOR UPDATE}, MySQL no
 * tiene nada que serializar y las dos pasan el filtro.</p>
 *
 * <p><b>Como se usa.</b> El servicio asegura la existencia de la fila en una
 * transaccion aparte y ya confirmada, y despues la bloquea con
 * {@code PESSIMISTIC_WRITE}. El lock se mantiene hasta el commit de la
 * transaccion que agendo la reserva.</p>
 *
 * <p><b>Sobre {@code cuposOcupados}.b> Es un contador desnormalizado y
 * <b>no es la fuente de verdad</b>: el conteo real se recalcula desde
 * {@code reserva_guarderia} dentro del lock. Si el contador se usara para
 * decidir, cualquier forgotten update (un borrado manual, un estado mal
 * seteado) dejaria el aforo desalineado de forma permanente. Aqui solo se
 * mantiene para observabilidad, y como se recalcula bajo el lock, no puede
 * desviarse.</p>
 */
@Entity
@Table(name = "control_aforo_diario",
		uniqueConstraints = @UniqueConstraint(name = "uk_control_aforo_fecha", columnNames = "fecha"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ControlAforoDiario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Dia al que aplica el semaforo. Unico a nivel de tabla. */
	@Column(name = "fecha", nullable = false, unique = true)
	private LocalDate fecha;

	/**
	 * Instantanea informativa de los cupos ocupados al ultimo cierre.
	 * No se usa para decidir si hay lugar.
	 */
	@Column(name = "cupos_ocupados", nullable = false)
	private int cuposOcupados = 0;
}