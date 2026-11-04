package com.veterinaria.veterinaria_backend.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.veterinaria.veterinaria_backend.entity.PlazaUrgenciaGuarderia;

public interface PlazaUrgenciaGuarderiaRepository extends JpaRepository<PlazaUrgenciaGuarderia, Long> {

	Optional<PlazaUrgenciaGuarderia> findByFecha(LocalDate fecha);

	boolean existsByFecha(LocalDate fecha);
}
