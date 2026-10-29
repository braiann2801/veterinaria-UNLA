package com.veterinaria.veterinaria_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuenta de un empleado para ingresar al sistema.
 *
 * <p>Las cuentas las administra el Duenio. La primera la siembra el backend
 * ({@code UsuarioInitializer}) con una contrasena por defecto y
 * {@link #debeCambiarPassword} en {@code true}.</p>
 *
 * <p>{@link #tokenVersion} viaja como claim {@code ver} en el JWT. Desactivar la
 * cuenta, resetear o cambiar la contrasena o cambiar el rol lo incrementa, y el
 * backend rechaza cualquier token emitido con la version anterior: una sesion
 * abierta en un celular cae en el siguiente request, sin esperar el
 * vencimiento.</p>
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Usuario {

	public enum Rol {
		DUENIO,
		VETERINARIO,
		RECEPCION,
		GUARDERIA
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Se guarda normalizado (trim + minusculas): el login no distingue mayusculas. */
	@Column(name = "username", nullable = false, unique = true, length = 40)
	private String username;

	/** Hash BCrypt. Nunca se serializa: los DTOs de respuesta no lo incluyen. */
	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "nombre_completo", nullable = false, length = 120)
	private String nombreCompleto;

	@Enumerated(EnumType.STRING)
	@Column(name = "rol", nullable = false, length = 20)
	private Rol rol;

	@Column(name = "activo", nullable = false)
	private boolean activo = true;

	@Column(name = "debe_cambiar_password", nullable = false)
	private boolean debeCambiarPassword = false;

	@Column(name = "token_version", nullable = false)
	private int tokenVersion = 0;

	/** Invalida todos los tokens emitidos hasta ahora para esta cuenta. */
	public void invalidarSesiones() {
		tokenVersion++;
	}
}
