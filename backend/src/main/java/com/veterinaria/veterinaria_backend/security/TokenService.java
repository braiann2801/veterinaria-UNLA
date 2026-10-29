package com.veterinaria.veterinaria_backend.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.veterinaria.veterinaria_backend.dto.LoginResponseDTO;
import com.veterinaria.veterinaria_backend.entity.Usuario;
import com.veterinaria.veterinaria_backend.mapper.EntityMapper;

/**
 * Emite los JWT de sesion (HS256).
 *
 * <p>Claims: {@code sub} = id del usuario y {@code ver} = su {@code tokenVersion}.
 * El rol NO se usa desde el token: {@link UsuarioJwtConverter} lo lee de la base
 * en cada request, asi un cambio de rol rige al instante.</p>
 */
@Service
public class TokenService {

	public static final String ISSUER = "veterinaria-unla";
	public static final String CLAIM_VERSION = "ver";

	private final JwtEncoder jwtEncoder;
	private final EntityMapper mapper;
	private final long horasDeVigencia;

	public TokenService(
			JwtEncoder jwtEncoder,
			EntityMapper mapper,
			@Value("${veterinaria.auth.token-horas:8}") long horasDeVigencia) {
		this.jwtEncoder = jwtEncoder;
		this.mapper = mapper;
		this.horasDeVigencia = horasDeVigencia;
	}

	public LoginResponseDTO emitir(Usuario usuario) {
		Instant ahora = Instant.now();
		Instant vence = ahora.plus(horasDeVigencia, ChronoUnit.HOURS);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject(usuario.getId().toString())
				.issuedAt(ahora)
				.expiresAt(vence)
				.claim(CLAIM_VERSION, usuario.getTokenVersion())
				.build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
		return new LoginResponseDTO(token, vence, mapper.toResponse(usuario));
	}
}
