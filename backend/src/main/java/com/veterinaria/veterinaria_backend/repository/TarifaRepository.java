package com.veterinaria.veterinaria_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.entity.Tarifa;

@Repository
public interface TarifaRepository extends JpaRepository<Tarifa, Long> {

	Optional<Tarifa> findByConcepto(ComandaCobro.Concepto concepto);

	List<Tarifa> findByVigenteTrueOrderByConceptoAsc();
}