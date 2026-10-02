package com.veterinaria.veterinaria_backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Jackson 3 movio databind a tools.jackson, pero las anotaciones siguen en
// el paquete historico com.fasterxml.jackson.annotation.
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resultado consolidado de validar un retiro en mostrador.
 *
 * <p>Es la unica pantalla que el recepcionista necesita para decidir si entrega
 * una mascota. Deliberadamente devuelve <b>el veredicto y sus razones</b>, no
 * la lista de checks: el mostrador no tiene que reinterpretar tres banderas,
 * solo leer por que se puede o no se puede.</p>
 *
 * <p>La consulta no falla cuando el retiro esta bloqueado. Bloquear es una
 * respuesta valida, no un error: si el endpoint devolviera 409, el frontend
 * tendria que atrapar la excepcion para poder pintar el semaforo en rojo.</p>
 *
 * @param puedeRetirar          veredicto final; solo lo decide {@code motivoBloqueo}
 * @param motivoBloqueo         por que no se puede entregar, o {@link MotivoBloqueo#NINGUNO}
 * @param tutorAutorizado       el vinculo existe con {@code autorizadoRetiro=true}
 * @param tieneDeuda            el tutor debe en comandas pendientes
 * @param saldoDeuda            saldo pendiente del tutor, 0.00 si no debe
 * @param alertaConducta        avisar tomar con precaucion al entregar: no bloquea
 * @param conducta              conducta base de la mascota segun la ficha maestra
 * @param conductaUltimaConsulta conducta observada en la ultima atencion, si hubo
 * @param fechaUltimaConsulta   cuando se registro esa atencion
 * @param mensaje               texto listo para mostrar en el semaforo
 * @param mascotaId             mascota a retirar
 * @param mascotaNombre          nombre de la mascota
 * @param tutorId               tutor que se presento
 * @param tutorNombre           nombre completo del tutor
 */
public record EgresoValidacionResponseDTO(
		boolean puedeRetirar,
		MotivoBloqueo motivoBloqueo,
		boolean tutorAutorizado,
		boolean tieneDeuda,
		BigDecimal saldoDeuda,
		boolean alertaConducta,
		String conducta,
		String conductaUltimaConsulta,
		LocalDateTime fechaUltimaConsulta,
		String mensaje,
		Long mascotaId,
		String mascotaNombre,
		Long tutorId,
		String tutorNombre) {

	/**
	 * Por que se bloquea el retiro.
	 *
	 * <p>La precedencia es fija y no arbitraria: primero la identidad de quien
	 * retira, despues la plata. Si alguien no autorizado esta debiendo, primero
	 * hay que resolver quien es: de nada sirve discutir el saldo con alguien a
	 * quien no le corresponde la mascota.</p>
	 *
	 * <p>{@code ALERTA_CONDUCTA} no aparece como motivo de bloqueo a proposito.
	 * Un perro que grune se entrega igual, con una recomendacion de tomarlo con
	 * deliberada precaution. Convertirlo en bloqueo seria un fallo operativo
	 * disfrazado de politica de seguridad.</p>
	 */
	public enum MotivoBloqueo {
		NINGUNO,
		TUTOR_NO_AUTORIZADO,
		DEUDA_PENDIENTE
	}

	/**
	 * Etiqueta visible del motivo, para el semáforo del mostrador.
	 *
	 * <p>Va en el JSON y no se calcula en el frontend a propósito: la palabra que
	 * ve el recepcionista tiene que ser siempre la misma, y duplicar el mapa de
	 * textos en dos lugares garantiza que algún día dejen de coincidir.</p>
	 *
	 * <p>El {@code @JsonProperty} hace falta porque, al no ser componente del
	 * record, Jackson no lo reconoce como getter por su nombre.</p>
	 */
	@JsonProperty("etiquetaMotivo")
	public String etiquetaMotivo() {
		return switch (motivoBloqueo) {
			case NINGUNO -> "Retiro permitido";
			case TUTOR_NO_AUTORIZADO -> "Tutor sin autorización de retiro";
			case DEUDA_PENDIENTE -> "Tutor con deuda pendiente";
		};
	}
}