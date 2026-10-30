package com.veterinaria.veterinaria_backend.entity;

import java.math.BigDecimal;

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
 * Extra del catalogo que el Duenio arma en Tarifas: una vacuna, una urgencia,
 * un insumo. El profesional lo elige al cerrar la atencion y su precio se suma
 * a la comanda de la consulta.
 *
 * <p>No se borra, se desactiva: las comandas ya emitidas guardan su propia copia
 * del nombre y del precio, pero un extra desactivado deja de ofrecerse.</p>
 */
@Entity
@Table(name = "extra",
		uniqueConstraints = @UniqueConstraint(name = "uk_extra_nombre", columnNames = "nombre"))
@Getter
@Setter
@NoArgsConstructor
public class Extra {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nombre", nullable = false, length = 80)
	private String nombre;

	@Column(name = "precio", nullable = false, precision = 12, scale = 2)
	private BigDecimal precio;

	@Column(name = "activo", nullable = false)
	private boolean activo = true;
}
