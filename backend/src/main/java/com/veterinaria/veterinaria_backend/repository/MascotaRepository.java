package com.veterinaria.veterinaria_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.Mascota;

@Repository
public interface MascotaRepository extends JpaRepository<Mascota, Long> {

	Optional<Mascota> findByChip(String chip);

	List<Mascota> findByNombreContainingIgnoreCase(String nombre);

	List<Mascota> findByEspecie(Mascota.Especie especie);
}