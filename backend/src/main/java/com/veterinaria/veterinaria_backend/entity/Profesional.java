package com.veterinaria.veterinaria_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Profesional clinico (veterinario o auxiliar) que atiende turnos.
 *
 * <p>Existe como entidad propia y no embebida en {@link TurnoClinico} para
 * poder validar solapamientos por profesional con una consulta sobre
 * {@code profesional_id}, y para ir aggregating la carga futura.</p>
 */
@Entity
@Table(name = "profesional")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Profesional {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nombre", nullable = false, length = 80)
	private String nombre;

	@Column(name = "apellido", nullable = false, length = 80)
	private String apellido;

	@Column(name = "matricula", unique = true, length = 40)
	private String matricula;

	@Column(name = "activo", nullable = false)
	private boolean activo = true;

	public String getNombreCompleto() {
		if (nombre == null && apellido == null) {
			return "";
		}
		if (apellido == null) {
			return nombre;
		}
		if (nombre == null) {
			return apellido;
		}
		return nombre + " " + apellido;
	}
}