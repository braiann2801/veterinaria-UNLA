package com.veterinaria.veterinaria_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.Profesional;
import com.veterinaria.veterinaria_backend.entity.TurnoClinico;

@Repository
public interface ProfesionalRepository extends JpaRepository<Profesional, Long> {

	Optional<Profesional> findByMatricula(String matricula);
}