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

import com.veterinaria.veterinaria_backend.entity.Tutor;

@Repository
public interface TutorRepository extends JpaRepository<Tutor, Long> {

	Optional<Tutor> findByDni(String dni);

	boolean existsByDni(String dni);

	List<Tutor> findByApellidoContainingIgnoreCase(String apellido);

	/**
	 * Ids de la pagina que coincide con el filtro. El DNI y el telefono se buscan
	 * con el valor ya normalizado a digitos (`dni`), para que "30.123.456"
	 * encuentre al tutor guardado como "30123456".
	 */
	@Query(value = """
			select t.id from Tutor t
			where (:incluirInactivos = true or t.activo = true)
			  and (:q = ''
			       or lower(t.nombre) like lower(concat('%', :q, '%'))
			       or lower(t.apellido) like lower(concat('%', :q, '%'))
			       or (:dni <> '' and (t.dni like concat('%', :dni, '%')
			           or (t.telefono is not null and t.telefono like concat('%', :dni, '%')))))
			""", countQuery = """
			select count(t) from Tutor t
			where (:incluirInactivos = true or t.activo = true)
			  and (:q = ''
			       or lower(t.nombre) like lower(concat('%', :q, '%'))
			       or lower(t.apellido) like lower(concat('%', :q, '%'))
			       or (:dni <> '' and (t.dni like concat('%', :dni, '%')
			           or (t.telefono is not null and t.telefono like concat('%', :dni, '%')))))
			""")
	Page<Long> buscarIds(@Param("q") String q, @Param("dni") String dni,
			@Param("incluirInactivos") boolean incluirInactivos, Pageable pageable);

	/** Carga en una sola consulta los tutores de la pagina con sus mascotas resumidas. */
	@EntityGraph(attributePaths = { "vinculos", "vinculos.mascota" })
	@Query("select distinct t from Tutor t where t.id in :ids")
	List<Tutor> findConMascotasPorIds(@Param("ids") Collection<Long> ids);
}
