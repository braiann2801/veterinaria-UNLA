package com.veterinaria.veterinaria_backend.security;

import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.veterinaria.veterinaria_backend.entity.Usuario;
import com.veterinaria.veterinaria_backend.repository.UsuarioRepository;

/**
 * Convierte un JWT ya validado (firma y vencimiento) en la autenticacion del request.
 *
 * <p>Lee el usuario de la base en cada request. Es una consulta por id contra una
 * tabla chica, y compra tres garantias que un token autocontenido no da:</p>
 * <ul>
 *   <li>Una cuenta desactivada o con la version de token cambiada (reset de clave,
 *       cambio de rol) deja de autenticar al instante: 401.</li>
 *   <li>El rol sale de la base, no del token.</li>
 *   <li>Con {@code debeCambiarPassword}, la unica autoridad es
 *       {@link #PASSWORD_CHANGE}: {@code SecurityConfig} solo le abre
 *       {@code /auth/me} y {@code /auth/cambiar-password}.</li>
 * </ul>
 */
@Component
public class UsuarioJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";

	private final UsuarioRepository usuarioRepository;

	public UsuarioJwtConverter(UsuarioRepository usuarioRepository) {
		this.usuarioRepository = usuarioRepository;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		Usuario usuario = buscar(jwt);
		Object version = jwt.getClaim(TokenService.CLAIM_VERSION);
		// Solo enteros exactos: intValue() truncaria 2.5 o 2+2^32 a la version real.
		boolean versionOk = usuario != null && (version instanceof Integer || version instanceof Long)
				&& ((Number) version).longValue() == usuario.getTokenVersion();
		if (usuario == null || !usuario.isActivo() || !versionOk) {
			throw new InvalidBearerTokenException("La sesión ya no es válida");
		}
		String autoridad = usuario.isDebeCambiarPassword()
				? PASSWORD_CHANGE
				: "ROLE_" + usuario.getRol().name();
		return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(autoridad)), usuario.getUsername());
	}

	private Usuario buscar(Jwt jwt) {
		try {
			return usuarioRepository.findById(Long.valueOf(jwt.getSubject())).orElse(null);
		} catch (NumberFormatException | NullPointerException ex) {
			return null;
		}
	}
}
