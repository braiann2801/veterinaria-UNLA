package com.veterinaria.veterinaria_backend.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.Medicamento;

/** Catálogo y stock de medicamentos. */
@Repository
public interface MedicamentoRepository extends JpaRepository<Medicamento, Long> {

	/**
	 * Listado paginado de activos, con búsqueda opcional por nombre, principio
	 * activo o laboratorio. El servicio pasa {@code ""} cuando no hay filtro,
	 * así que la comparación con la cadena vacía evita duplicar consultas.
	 *
	 * <p>El orden lo agrega Spring a partir del {@code Sort} del {@code Pageable}
	 * del servicio (nombre y, como desempate, id).</p>
	 */
	@Query("""
			SELECT m FROM Medicamento m
			WHERE m.activo = true
			  AND (:q = '' OR
			       LOWER(m.nombre) LIKE LOWER(CONCAT('%', :q, '%')) OR
			       LOWER(COALESCE(m.principioActivo, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR
			       LOWER(COALESCE(m.laboratorio, '')) LIKE LOWER(CONCAT('%', :q, '%')))
			""")
	Page<Medicamento> buscar(@Param("q") String q, Pageable pageable);

	/** Duplicados por identidad normalizada, sin depender de mayúsculas ni acentos. */
	Optional<Medicamento> findByNombreNormalizadoAndPresentacionNormalizada(
			String nombreNormalizado, String presentacionNormalizada);

	/**
	 * Bloqueo pesimista de la fila para serializar cargas y consumos concurrentes.
	 *
	 * <p>SQL nativo y no {@code @Lock(PESSIMISTIC_WRITE)}: Hibernate 7 genera
	 * {@code FOR UPDATE OF alias}, sintaxis que MariaDB rechaza (mismo problema
	 * que en {@code ControlAforoDiarioRepository}). El lock se libera al commit
	 * de la transacción del servicio.</p>
	 */
	@Query(value = "SELECT * FROM medicamento WHERE id = :id FOR UPDATE", nativeQuery = true)
	Optional<Medicamento> bloquearPorId(@Param("id") Long id);
}
