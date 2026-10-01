package com.veterinaria.veterinaria_backend.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tutor (dueño o cotutor) de una o varias mascotas.
 *
 * <p>La relación con {@link Mascota} es N:M y se materializa a través de la
 * entidad {@link TutorMascota}, que registra además el flag
 * {@code autorizadoRetiro} exigido por las reglas de negocio.</p>
 */
@Entity
@Table(name = "tutor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Tutor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nombre", nullable = false, length = 80)
	private String nombre;

	@Column(name = "apellido", nullable = false, length = 80)
	private String apellido;

	@Column(name = "dni", nullable = false, unique = true, length = 20)
	private String dni;

	@Column(name = "telefono", length = 30)
	private String telefono;

	@Column(name = "email", length = 120)
	private String email;

	@Column(name = "direccion", length = 200)
	private String direccion;

	/**
	 * Lados de la relacion N:M. Sin {@code orphanRemoval}: la tupla tiene dos
	 * propietario (Tutor y Mascota) y el borrado en cascada desde ambos lados
	 * provoca {@code DuplicateKeyException} cuando el servicio recrea la misma
	 * tupla en la misma transaccion. Las bajas las resuelve el servicio con
	 * {@code TutorMascotaRepository#delete} explicito.
	 */
	@OneToMany(mappedBy = "tutor", cascade = CascadeType.ALL)
	private List<TutorMascota> vinculos = new ArrayList<>();

	/** Nombre completo para presentations y listados. */
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