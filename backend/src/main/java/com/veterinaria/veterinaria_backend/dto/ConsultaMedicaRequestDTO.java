package com.veterinaria.veterinaria_backend.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;

import com.veterinaria.veterinaria_backend.config.FlexibleLocalDateTimeDeserializer;
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

		/**
		 * Instante de la atencion. Si no llega, el servidor usa el momento de la
		 * ficha.
		 *
		 * <p>Acepta tambien {@code YYYY-MM-DD} a secas, que es lo que entrega el
		 * {@code <input type="date">} del frontend. Sin esa tolerancia Jackson
		 * devolvia 400 y la pantalla no podia guardar ninguna ficha: ver
		 * {@link FlexibleLocalDateTimeDeserializer}.</p>
		 */
		@JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
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
		ConductaRequestDTO conductaObservada,
		/** Extras usados en la atencion (vacunas, urgencias). Sin precio: lo pone el catalogo. */
		List<@Valid ExtraUsadoDTO> extras) {

	public record ExtraUsadoDTO(
			@NotNull(message = "El id del extra es obligatorio")
			Long extraId,
			@NotNull(message = "La cantidad es obligatoria")
			@Min(value = 1, message = "La cantidad debe ser al menos 1")
			// Mismo tope que la pantalla: evita una comanda desproporcionada por un error de tipeo o de la API.
			@Max(value = 99, message = "La cantidad no puede superar 99")
			Integer cantidad) {
	}


	public enum ConductaRequestDTO {
		TRANQUILO,
		NERVIOSO,
		REACTIVO,
		AGRESIVO
	}
}