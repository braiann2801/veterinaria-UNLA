package com.veterinaria.veterinaria_backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.Mascota;

@Repository
public interface MascotaRepository extends JpaRepository<Mascota, Long> {

	Optional<Mascota> findByChip(String chip);

	List<Mascota> findByNombreContainingIgnoreCase(String nombre);

	List<Mascota> findByEspecie(Mascota.Especie especie);

	/**
	 * Ids de la pagina que coincide con el filtro. Se pagina aca, sobre la tabla
	 * de mascotas, para no paginar en memoria una consulta con join de coleccion.
	 */
	@Query(value = """
			select m.id from Mascota m
			where (:incluirInactivos = true or m.activo = true)
			  and (:q = '' or lower(m.nombre) like lower(concat('%', :q, '%')))
			""", countQuery = """
			select count(m) from Mascota m
			where (:incluirInactivos = true or m.activo = true)
			  and (:q = '' or lower(m.nombre) like lower(concat('%', :q, '%')))
			""")
	Page<Long> buscarIds(@Param("q") String q, @Param("incluirInactivos") boolean incluirInactivos,
			Pageable pageable);

	/** Carga en una sola consulta las mascotas de la pagina con sus tutores resumidos. */
	@EntityGraph(attributePaths = { "vinculos", "vinculos.tutor" })
	@Query("select distinct m from Mascota m where m.id in :ids")
	List<Mascota> findConTutoresPorIds(@Param("ids") Collection<Long> ids);
}
