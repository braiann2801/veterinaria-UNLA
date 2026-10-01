package com.veterinaria.veterinaria_backend.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.ControlAforoDiario;

/**
 * Acceso al semaforo de aforo por dia.
 *
 * <p><b>Por que el lock se escribe en SQL nativo y no con {@code @Lock}.</b>
 * Al combinar un {@code @Query} JPQL con {@code @Lock(PESSIMISTIC_WRITE)},
 * Hibernate 7 compone la sentencia como
 * {@code ... FOR UPDATE OF alias}. La clausula {@code OF} es sintaxis de
 * Oracle/PostgreSQL: MariaDB la rechaza con error 1064 y el repository falla al
 * arrancar. La variante derivada tampoco sirve como evasion, porque Spring
 * parsea el sufijo {@code ForUpdate} como nombre de propiedad.</p>
 *
 * <p>Escribir el {@code FOR UPDATE} a mano deja el SQL exacto a la vista y
 * elimina esa capa de traduccion. Es la opcion que el motor de MariaDB
 * entiende de forma nativa.</p>
 */
@Repository
public interface ControlAforoDiarioRepository extends JpaRepository<ControlAforoDiario, Long> {

	/** Lectura simple, sin lock. Para consultas de solo lectura. */
	Optional<ControlAforoDiario> findByFecha(LocalDate fecha);

	/**
	 * Adquiere el lock pesimista de escritura sobre la fila del dia
	 * ({@code SELECT ... FOR UPDATE}).
	 *
	 * <p>Es lo que serializa las reservas concurrentes: la segunda transaccion
	 * queda esperando y recien cuando la primera confirma lee el conteo, ya
	 * actualizado. El lock se mantiene hasta el commit de la transaccion
	 * llamadora, porque en InnoDB los bloqueos de fila se liberan al confirmar
	 * o revertir, nunca antes.</p>
	 *
	 * <p>Devuelve {@code Optional.empty} si el dia aun no tiene semaforo; el
	 * servicio lo crea antes de reintentar el lock.</p>
	 *
	 * <p>Sin {@code @Lock} a proposito: el modo se ya escribio en la sentencia.
	 * Agregarlo ademas seria redundante y reintroduciria el problema del
	 * {@code FOR UPDATE OF}.</p>
	 */
	@Query(value = "SELECT * FROM control_aforo_diario WHERE fecha = :fecha FOR UPDATE",
			nativeQuery = true)
	Optional<ControlAforoDiario> bloquearPorFecha(@Param("fecha") LocalDate fecha);
}