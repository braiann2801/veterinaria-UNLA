package com.veterinaria.veterinaria_backend.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.EgresoMascota;

@Repository
public interface EgresoMascotaRepository extends JpaRepository<EgresoMascota, Long> {

	boolean existsByMascotaIdAndFecha(Long mascotaId, LocalDate fecha);

	List<EgresoMascota> findByFecha(LocalDate fecha);
}
