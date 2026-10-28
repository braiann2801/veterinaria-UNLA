package com.veterinaria.veterinaria_backend.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.veterinaria.veterinaria_backend.exception.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Seguridad de la API: JWT stateless y matriz de permisos por rol.
 *
 * <p>Sin CSRF a proposito: el token viaja en {@code Authorization}, no en una
 * cookie, asi que un sitio de terceros no puede hacer que el navegador lo mande
 * solo. Sin sesion HTTP: cada request trae su token.</p>
 *
 * <p>Los permisos se aplican aca y no en el frontend. Que el sidebar esconda
 * Balance no protege nada si {@code GET /caja/cierre} responde a cualquiera.</p>
 *
 * <p>CORS: {@code http.cors()} reutiliza las reglas de {@code CorsConfig}, asi
 * los 401/403 tambien llevan los headers y el frontend puede leerlos.</p>
 */
@Configuration
public class SecurityConfig {

	private static final String[] EMPLEADOS = { "DUENIO", "VETERINARIO", "RECEPCION", "GUARDERIA" };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, UsuarioJwtConverter converter,
			AuthenticationEntryPoint entryPoint, AccessDeniedHandler accessDeniedHandler) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
						.requestMatchers("/error").permitAll()
						// Disponibles aun con el cambio de clave pendiente (autoridad PASSWORD_CHANGE).
						.requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/v1/auth/cambiar-password").authenticated()
						// Solo el Duenio.
						.requestMatchers("/api/v1/usuarios/**").hasRole("DUENIO")
						.requestMatchers(HttpMethod.GET, "/api/v1/caja/cierre").hasRole("DUENIO")
						.requestMatchers(HttpMethod.PUT, "/api/v1/comandas/tarifas/**").hasRole("DUENIO")
						// Cualquier empleado con la clave ya cambiada.
						.requestMatchers("/api/**").hasAnyRole(EMPLEADOS)
						.anyRequest().authenticated())
				.oauth2ResourceServer(rs -> rs
						.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecretKey jwtSecretKey(@Value("${veterinaria.auth.jwt-secret:dev-solo-local-cambiar-en-produccion-0123456789}") String secreto) {
		byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
		// HS256 exige una clave de al menos 256 bits. Se corta el arranque en vez
		// de emitir tokens con una clave adivinable.
		if (bytes.length < 32) {
			throw new IllegalStateException("veterinaria.auth.jwt-secret (JWT_SECRET) debe tener al menos 32 bytes");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
		return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
	}

	/** 401 con el mismo contrato {@link ApiErrorResponse} que el resto de los errores. */
	@Bean
	AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
		return (request, response, ex) -> escribir(objectMapper, request, response, HttpStatus.UNAUTHORIZED,
				"Unauthorized", "Hace falta iniciar sesión, o la sesión venció");
	}

	/** 403 con el mismo contrato {@link ApiErrorResponse} que el resto de los errores. */
	@Bean
	AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
		return (request, response, ex) -> escribir(objectMapper, request, response, HttpStatus.FORBIDDEN,
				"Forbidden", "El usuario no tiene permiso para esta acción");
	}

	private static void escribir(ObjectMapper objectMapper, HttpServletRequest request, HttpServletResponse response,
			HttpStatus status, String error, String mensaje) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getOutputStream(),
				ApiErrorResponse.of(status.value(), error, mensaje, request.getRequestURI()));
	}
}
