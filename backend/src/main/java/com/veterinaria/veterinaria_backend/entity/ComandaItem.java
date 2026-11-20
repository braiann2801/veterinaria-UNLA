package com.veterinaria.veterinaria_backend.entity;

import java.math.BigDecimal;

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
 * Linea de detalle de una comanda: la consulta o un medicamento usado en la
 * atencion.
 *
 * <p>Descripcion y precio se copian al emitir: si cambia el precio de catalogo
 * despues, la comanda ya emitida no se mueve. No hay relacion JPA con el
 * medicamento: el historial vive en estos escalares, aunque se borre del
 * catalogo.</p>
 */
@Entity
@Table(name = "comanda_item")
@Getter
@Setter
@NoArgsConstructor
public class ComandaItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "comanda_id", nullable = false)
	private ComandaCobro comanda;

	@Column(name = "descripcion", nullable = false, length = 80)
	private String descripcion;

	@Column(name = "cantidad", nullable = false)
	private int cantidad;

	@Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
	private BigDecimal precioUnitario;

	@Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
	private BigDecimal subtotal;
}
