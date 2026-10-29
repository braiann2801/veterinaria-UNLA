package com.veterinaria.veterinaria_backend.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Precios de la guarderia: por hora (estadias del mismo dia) y por noche.
 *
 * <p>Fila unica. Va aparte de {@link Tarifa} porque la guarderia tiene dos
 * precios y la tabla de tarifas es uno por concepto de cobro: meter el precio
 * por hora como un concepto mas lo volveria cobrable suelto en una comanda.</p>
 */
@Entity
@Table(name = "tarifa_guarderia")
@Getter
@Setter
@NoArgsConstructor
public class TarifaGuarderia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "precio_hora", nullable = false, precision = 12, scale = 2)
	private BigDecimal precioHora;

	@Column(name = "precio_noche", nullable = false, precision = 12, scale = 2)
	private BigDecimal precioNoche;
}
