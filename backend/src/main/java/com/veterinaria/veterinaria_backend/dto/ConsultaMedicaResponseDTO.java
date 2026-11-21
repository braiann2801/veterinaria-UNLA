package com.veterinaria.veterinaria_backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vista publica de una ficha clinica.
 *
 * <p><b>Tampoco expone campos contables.</b> No hay monto, ni saldo, ni medio
 * de pago. La unica referencia al cobro es el {@code comandaId}, que es la
 * llave para que el mostrador consulte el detalle en
 * {@code /api/v1/comandas/{id}}. Deliberadamente es un id y no un objeto
 * anidado: anidar la comanda completa meteria el saldo dentro de la respuesta
 * clinica y reintroduiria el acoplamiento que la regla 2.3 prohibe.</p>
 *
 * <p>Mascota y profesional van como resumenes planos: cada uno tiene su propia
 * relacion N:M y arrastrarlos enteros reintroduce el ciclo de serializacion
 * que TASK-002 elimino.</p>
 */
public record ConsultaMedicaResponseDTO(
		Long id,
		Long turnoClinicoId,
		LocalDateTime fechaAtencion,
		String anamnesis,
		String diagnostico,
		String tratamiento,
		java.math.BigDecimal pesoKg,
		String conductaObservada,
		boolean alertaConducta,
		MascotaFichaResumenDTO mascota,
		ProfesionalFichaResumenDTO profesional,
		Long comandaId,
		/** Copia de lo aplicado en la atención; sobrevive a la baja del catálogo. Sin precios. */
		List<MedicamentoAplicadoDTO> medicamentos) {

	public record MascotaFichaResumenDTO(Long id, String nombre, String especie) {
	}

	public record ProfesionalFichaResumenDTO(Long id, String nombreCompleto, String matricula) {
	}

	/**
	 * Dato clínico, no contable: nombre y presentación congelados al atender y
	 * cantidad aplicada. No expone monto ni precio (regla 3 de AGENTS.md).
	 */
	public record MedicamentoAplicadoDTO(
			Long medicamentoId,
			String nombre,
			String presentacion,
			int cantidad) {
	}
}