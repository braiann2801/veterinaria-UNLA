package com.veterinaria.veterinaria_backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.veterinaria.veterinaria_backend.entity.Usuario;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByUsername(String username);

	boolean existsByUsername(String username);

	long countByRolAndActivoTrue(Usuario.Rol rol);

	List<Usuario> findAllByOrderByNombreCompletoAsc();
}
