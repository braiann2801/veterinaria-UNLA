package com.veterinaria.veterinaria_backend.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Precio vigente de un concepto cobrable.
 *
 * <p><b>Por que existe.</b> La regla 2.3 exige que la ficha clinica no
 * almacene montos. Pero la comanda que se emite al guardar la consulta necesita
 * un {@code montoTotal}, y ese valor tiene que venir de algun lado. Sin esta
 * tabla habria dos salidas, ambas malas: guardar el precio dentro de la entidad
 * clinica (que es justamente lo que la regla prohibe) o hardcodear una
 * constante en el servicio (que hace imposible cambiar precios sin recompilar).
 * La tarifa separa el precio del dato clinico.</p>
 *
 * <p>Se modela aparte de {@link ComandaCobro} a proposito: el precio es una
 * decision del mostrador, no un hecho de la atencion. La comanda congela el
 * monto al momento de emitirse, asi que cambiar la tarifa despues no altera
 * las comandas ya emitidas.</p>
 */
@Entity
@Table(name = "tarifa",
		uniqueConstraints = @UniqueConstraint(name = "uk_tarifa_concepto", columnNames = "concepto"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Tarifa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * Vocabulario compartido con {@link ComandaCobro}. Se reutiliza su enum en
	 * lugar de declarar uno propio: dos enums paralelos de conceptos obligarian
	 * a mantenerlos sincronizados a mano y tarde o temprano alguno quedaria sin
	 * tarifa, que es justo el fallo que el servicio rechaza.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "concepto", nullable = false, length = 30)
	private ComandaCobro.Concepto concepto;

	/** Precio unitario con dos decimales. */
	@Column(name = "monto", nullable = false, precision = 12, scale = 2)
	private BigDecimal monto;

	@Column(name = "descripcion", length = 150)
	private String descripcion;

	/**
	 * Solo una tarifa vigente por concepto (garantizado por la constraint UNIQUE).
	 * Para cambiar un precio se edita la fila existente en vez de insertar otra.
	 */
	@Column(name = "vigente", nullable = false)
	private boolean vigente = true;
}