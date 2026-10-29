package com.veterinaria.veterinaria_backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.TarifaGuarderia;

@Repository
public interface TarifaGuarderiaRepository extends JpaRepository<TarifaGuarderia, Long> {

	/** La configuracion es una fila unica; si hubiera mas, manda la primera. */
	Optional<TarifaGuarderia> findFirstByOrderByIdAsc();
}
