package com.veterinaria.veterinaria_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.TutorMascota;
import com.veterinaria.veterinaria_backend.entity.TutorMascotaId;

@Repository
public interface TutorMascotaRepository extends JpaRepository<TutorMascota, TutorMascotaId> {

	List<TutorMascota> findByTutorId(Long tutorId);

	List<TutorMascota> findByMascotaId(Long mascotaId);

	List<TutorMascota> findByMascotaIdAndAutorizadoRetiroTrue(Long mascotaId);

	Optional<TutorMascota> findByTutorIdAndMascotaId(Long tutorId, Long mascotaId);
}