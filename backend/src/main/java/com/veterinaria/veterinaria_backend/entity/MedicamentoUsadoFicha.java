package com.veterinaria.veterinaria_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Copia de lo que se aplicó en una ficha clínica, guardada al emitir la ficha.
 *
 * <p>El nombre y la presentación son escalales, no una referencia al catálogo:
 * renombrar, desactivar o borrar un medicamento no puede alterar lo que el
 * veterinario registró en ese momento. El {@code medicamentoId} se conserva
 * como dato histórico, sin FK, por el mismo motivo. Tampoco hay precio acá:
 * la ficha sigue sin campos contables (regla 3 de AGENTS.md); el monto congelado
 * vive en {@link ComandaItem}.</p>
 */
@Entity
@Table(name = "medicamento_usado_ficha")
@Getter
@Setter
@NoArgsConstructor
public class MedicamentoUsadoFicha {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "consulta_medica_id", nullable = false)
	private ConsultaMedica consultaMedica;

	/** Id del ítem de catálogo usado. Sin FK: la ficha sobrevive a su borrado. */
	@Column(name = "medicamento_id", nullable = false)
	private Long medicamentoId;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(length = 120)
	private String presentacion;

	@Column(nullable = false)
	private int cantidad;
}
