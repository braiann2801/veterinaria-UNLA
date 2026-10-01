package com.veterinaria.veterinaria_backend.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload de alta de ficha clinica.
 *
 * <p><b>Aca se materializa el desacople clinico-contable (AGENTS.md 2.3).</b>
 * Este record no declara ningun campo de monto, medio de pago, saldo, total ni
 * forma de cobro. No es una omision: es el contrato. La pantalla del
 * veterinario no puede mandar un precio porque el endpoint no tiene donde
 * recibirlo, y no puede mostrar un saldo porque la respuesta tampoco lo
 * incluye. El precio sale de {@code Tarifa} y el cobro se resuelve en el
 * mostrador contra {@link ComandaCobroResponseDTO}.</p>
 *
 * <p>El servicio emite la comanda en PENDIENTE al guardar la ficha, con el
 * monto congelado de la tarifa vigente en ese momento.</p>
 */
public record ConsultaMedicaRequestDTO(

		/** Opcional: las consultas de urgencia no pasan por turno. */
		Long turnoClinicoId,

		@NotNull(message = "El id de la mascota es obligatorio")
		Long mascotaId,

		@NotNull(message = "El id del profesional es obligatorio")
		Long profesionalId,

		/** Si no llega, el servidor usa el instante de la atencion. */
		LocalDateTime fechaAtencion,

		@NotBlank(message = "La anamnesis es obligatoria")
		@Size(max = 2000, message = "La anamnesis no puede superar los 2000 caracteres")
		String anamnesis,

		@Size(max = 2000, message = "El diagnostico no puede superar los 2000 caracteres")
		String diagnostico,

		@Size(max = 2000, message = "El tratamiento no puede superar los 2000 caracteres")
		String tratamiento,

		/**
		 * Conducta observada. La alerta NO se recibe: el servidor la deriva de
		 * este valor. Aceptarla por API permitiria mandarle {@code false} a un
		 * animal agresivo y perder la advertencia en silencio.
		 */
		ConductaRequestDTO conductaObservada) {

	public enum ConductaRequestDTO {
		TRANQUILO,
		NERVIOSO,
		REACTIVO,
		AGRESIVO
	}
}