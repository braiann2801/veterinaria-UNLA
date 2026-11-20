package com.veterinaria.veterinaria_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Historial inmutable de entradas y salidas de inventario. Es la pieza que
 * deja preparado el descuento desde la ficha clínica: el movimiento ya tiene
 * un tipo {@link TipoMovimiento#EGRESO_FICHA} y un {@code referenciaId} para
 * guardar el id de la consulta cuando se conecte ese flujo.
 *
 * <p>{@code usuario} es un snapshot del username y no una FK: las cuentas se
 * borran físicamente en {@code UsuarioServiceImpl.eliminar}, y borrar al
 * operador no puede llevarse el historial de lo que hizo. Tampoco hay FK a la
 * consulta: el inventario no se acopla todavía a la ficha clínica.</p>
 */
@Entity
@Table(name = "movimiento_stock_medicamento")
@Getter
@Setter
@NoArgsConstructor
public class MovimientoStockMedicamento {

	/** Natura del movimiento. {@code AJUSTE} queda disponible para correcciones manuales futuras. */
	public enum TipoMovimiento {
		INGRESO,
		EGRESO_FICHA,
		AJUSTE
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "medicamento_id", nullable = false)
	private Medicamento medicamento;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TipoMovimiento tipo;

	@Column(nullable = false)
	private int cantidad;

	@Column(name = "stock_resultante", nullable = false)
	private int stockResultante;

	@Column(length = 500)
	private String observacion;

	/** Id de la ficha/consulta que motivó el egreso. Nulo en ingresos y ajustes. */
	@Column(name = "referencia_id")
	private Long referenciaId;

	@Column(nullable = false, length = 80)
	private String usuario;

	@Column(name = "fecha_hora", nullable = false)
	private LocalDateTime fechaHora;
}
