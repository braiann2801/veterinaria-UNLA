package com.veterinaria.veterinaria_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Insumo del consultorio con stock controlado (vacunas, medicamentos,
 * materiales). El alta y la carga de stock son las únicas escrituras de este
 * bloque: no se edita ni se desactiva, pero el campo {@code activo} ya está
 * modelado y el listado solo devuelve los activos.
 *
 * <p>La identidad del catálogo es el par (nombre, presentación) normalizado.
 * "Amoxicilina" en comprimidos y "Amoxicilina" en suspensión son dos productos
 * distintos; "amoxicilina 500 mg" y "AMOXICILINA 500mg" con la misma
 * presentación son el mismo. Las claves se guardan en columnas aparte para que
 * MySQL haga respetar el constraint sin depender del {@code LOWER} de cada
 * consulta ni de la collation de la base.</p>
 */
@Entity
@Table(name = "medicamento", uniqueConstraints = @UniqueConstraint(
		name = "uk_medicamento_clave",
		columnNames = { "nombre_normalizado", "presentacion_normalizada" }))
@Getter
@Setter
@NoArgsConstructor
public class Medicamento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String nombre;

	@Column(name = "nombre_normalizado", nullable = false, length = 120)
	private String nombreNormalizado;

	@Column(length = 120)
	private String presentacion;

	/**
	 * Clave de presentación sin normalizar a null: cuando el alta no trae
	 * presentación se guarda {@code ""}. En MySQL un NULL no choca con el
	 * constraint único, así que dos altas sin presentación podrían duplicarse.
	 */
	@Column(name = "presentacion_normalizada", nullable = false, length = 120)
	private String presentacionNormalizada = "";

	@Column(name = "principio_activo", length = 120)
	private String principioActivo;

	@Column(length = 120)
	private String laboratorio;

	@Column(name = "stock_actual", nullable = false)
	private int stockActual = 0;

	@Column(name = "stock_minimo", nullable = false)
	private int stockMinimo = 0;

	/**
	 * Precio unitario que se cobra al cliente junto con la consulta. Se copia a
	 * la comanda al emitir: cambiarlo aca nunca altera una comanda ya emitida.
	 * La columna se agrega con {@code ddl-auto=update}, asi que las filas
	 * anteriores llegan nulas y se interpretan como cero.
	 */
	@Column(name = "precio", precision = 12, scale = 2)
	private BigDecimal precio = BigDecimal.ZERO;

	@Column(nullable = false)
	private boolean activo = true;

	@Column(name = "fecha_actualizacion", nullable = false)
	private LocalDateTime fechaActualizacion;

	/** Precio con dos decimales; cero para los medicamentos cargados antes del campo. */
	public BigDecimal precioCobrable() {
		return precio == null ? BigDecimal.ZERO.setScale(2) : precio.setScale(2, java.math.RoundingMode.HALF_UP);
	}
}
