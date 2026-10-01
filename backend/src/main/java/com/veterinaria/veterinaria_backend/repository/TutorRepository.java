package com.veterinaria.veterinaria_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.Tutor;

@Repository
public interface TutorRepository extends JpaRepository<Tutor, Long> {

	Optional<Tutor> findByDni(String dni);

	boolean existsByDni(String dni);

	List<Tutor> findByApellidoContainingIgnoreCase(String apellido);
}