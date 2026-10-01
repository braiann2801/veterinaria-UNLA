package com.veterinaria.veterinaria_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Paciente animal del sistema. Se relaciona N:M con {@link Tutor} mediante
 * {@link TutorMascota}.
 */
@Entity
@Table(name = "mascota")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Mascota {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nombre", nullable = false, length = 80)
	private String nombre;

	@Enumerated(EnumType.STRING)
	@Column(name = "especie", nullable = false, length = 30)
	private Especie especie;

	@Column(name = "raza", length = 80)
	private String raza;

	@Column(name = "sexo", length = 20)
	private String sexo;

	@Column(name = "fecha_nacimiento")
	private LocalDate fechaNacimiento;

	/** BigDecimal: Hibernate no admite scale en tipos flotantes (float/double). */
	@Column(name = "peso_kg", precision = 6, scale = 2)
	private BigDecimal pesoKg;

	@Enumerated(EnumType.STRING)
	@Column(name = "conducta", length = 20)
	private Conducta conducta;

	@Column(name = "chip", unique = true, length = 40)
	private String chip;

	@Column(name = "observaciones", length = 500)
	private String observaciones;

	/** Ver nota en {@link Tutor#vinculos}: sin orphanRemoval. */
	@OneToMany(mappedBy = "mascota", cascade = CascadeType.ALL)
	private List<TutorMascota> vinculos = new ArrayList<>();

	public enum Especie {
		CANINO,
		FELINO,
		AVE,
		ROEDOR,
		REPTIL,
		OTRO
	}

	/**
	 * Valores en ASCII a proposito: Hibernate mapea enums STRING a un
	 * {@code enum} de MySQL, y un literal con ñ se guardaba corrupto
	 * ({@code 'GRU?E'}) por el charset de la conexion. La API expone el
	 * valor con ñ en {@code MascotaResponseDTO} y el mapper traduce.
	 */
	public enum Conducta {
		NORMAL,
		GRUNE,
		MUERDE,
		MIEDOSO
	}
}