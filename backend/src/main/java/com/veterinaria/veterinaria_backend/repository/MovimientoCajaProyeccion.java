package com.veterinaria.veterinaria_backend.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.entity.Pago;

/**
 * Proyeccion plana de un pago para el extracto de caja.
 *
 * <p>Es una interfaz y no una entidad a proposito: la consulta trae solo las
 * columnas que la vista necesita, sin inicializar relaciones LAZY ni exponer
 * objetos gestionados por JPA fuera de la transaccion. El mapeo a
 * {@code MovimientoCajaDTO} lo hace el servicio, que es quien traduce el enum a
 * su nombre.</p>
 */
public interface MovimientoCajaProyeccion {

	Long getId();

	LocalDateTime getFechaHora();

	/** Emision de la comanda: distingue un cobro del dia de un saldo de otro dia. */
	LocalDateTime getFechaEmision();

	Pago.MetodoPago getMetodoPago();

	BigDecimal getMonto();

	ComandaCobro.Concepto getConcepto();

	String getNombreTutor();

	String getNombreMascota();

	ComandaCobro.EstadoComanda getEstadoComanda();
}
