package com.veterinaria.veterinaria_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.Extra;

@Repository
public interface ExtraRepository extends JpaRepository<Extra, Long> {

	List<Extra> findAllByOrderByNombreAsc();

	List<Extra> findByActivoTrueOrderByNombreAsc();

	/** El nombre identifica al extra sin importar mayusculas: "Vacuna" y "vacuna" son el mismo. */
	Optional<Extra> findByNombreIgnoreCase(String nombre);
}
