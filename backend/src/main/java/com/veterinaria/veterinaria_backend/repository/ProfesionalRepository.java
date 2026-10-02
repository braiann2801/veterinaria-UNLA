package com.veterinaria.veterinaria_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.Profesional;

@Repository
public interface ProfesionalRepository extends JpaRepository<Profesional, Long> {

	Optional<Profesional> findByMatricula(String matricula);

	/**
	 * Busca por DNI exacto.
	 *
	 * <p>Devuelve lista y no {@code Optional} por una razon concreta: la columna
	 * es nullable, y MySQL admite varios NULL en una UNIQUE. Un
	 * {@code Optional} obligaria al llamador a decidir que hacer con "no hay
	 * ninguno" y con "hay dos" como si fueran el mismo caso. La lista lo separa.
	 *
	 * <p>El servicio normaliza a mayusculas y recorta antes de llamar, asi que
	 * este finder no necesita hacer collation ni trim: lo que se busca ya esta
	 * en la misma forma en que se guardo.</p>
	 */
	List<Profesional> findByDni(String dni);
}