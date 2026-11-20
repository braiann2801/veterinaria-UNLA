package com.veterinaria.veterinaria_backend.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS para el frontend Next.js en desarrollo y en produccion.
 *
 * <p>Sin configuracion explicita solo se aceptan los origenes locales de
 * desarrollo. En cualquier despliegue (Vercel, Render o hosting propio) se
 * define {@code CORS_ORIGENES} (lista separada por comas) con la URL exacta del
 * frontend, que pasa a tener prioridad total sobre el default.</p>
 *
 * <p>Cuando {@code CORS_ORIGENES} esta definida se exige la URL exacta de cada
 * deploy: los comodines amplios de Vercel (por ejemplo
 * {@code https://*.vercel.app}) se descartan con un aviso en el log. Asi una
 * aplicacion ajena alojada en Vercel no puede consumir la API solo por compartir
 * dominio de hosting.</p>
 *
 * <p>No se habilitan credenciales: el JWT viaja en el header
 * {@code Authorization}, no en una cookie. Sin {@code allowCredentials}, un
 * sitio ajeno no puede leer respuestas autenticadas aunque comparta origen
 * permitido, y se elimina la combinacion mas riesgosa de CORS (patron comodin +
 * credenciales).</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

	/** Origenes de desarrollo local (AGENTS.md, seccion 1). */
	static final String ORIGENES_LOCALES = "http://localhost:3000,http://127.0.0.1:3000";

	/**
	 * Default agnostico al hosting: solo los origenes locales. Cualquier
	 * despliegue (Vercel, Render o hosting propio) define {@code CORS_ORIGENES}
	 * con la URL exacta de su frontend.
	 */
	static final String ORIGENES_POR_DEFECTO = ORIGENES_LOCALES;

	private final String[] origenes;

	public CorsConfig(
			@Value("${CORS_ORIGENES:}") String origenesDesdeEntorno,
			@Value("${veterinaria.cors.origenes:" + ORIGENES_POR_DEFECTO + "}") String origenesDesdePropiedad) {
		boolean explicito = origenesDesdeEntorno != null && !origenesDesdeEntorno.isBlank();
		this.origenes = resolverOrigenes(explicito ? origenesDesdeEntorno : origenesDesdePropiedad, explicito);
	}

	/**
	 * Normaliza la lista de origenes. Con configuracion explicita descarta los
	 * comodines amplios de Vercel; si no queda ninguno valido vuelve a los
	 * origenes locales para no dejar el frontend de desarrollo sin acceso.
	 */
	static String[] resolverOrigenes(String valor, boolean explicito) {
		String[] lista = Arrays.stream(valor == null ? new String[0] : valor.split(","))
				.map(String::trim)
				.filter(origen -> !origen.isEmpty())
				.toArray(String[]::new);

		if (!explicito) {
			return lista;
		}

		List<String> exactos = new ArrayList<>(lista.length);
		for (String origen : lista) {
			if (esComodinAmplioVercel(origen)) {
				log.warn("CORS_ORIGENES contiene el comodin amplio '{}'; se ignora. "
						+ "Configure la URL exacta del deploy (por ejemplo https://mi-app.vercel.app).", origen);
			} else {
				exactos.add(origen);
			}
		}

		if (exactos.isEmpty()) {
			log.warn("CORS_ORIGENES no dejo origenes validos; se usan los de desarrollo local ({}).",
					ORIGENES_LOCALES);
			return ORIGENES_LOCALES.split(",");
		}
		return exactos.toArray(String[]::new);
	}

	/**
	 * Un comodin es "amplio de Vercel" si incluye {@code *} y su host es
	 * {@code vercel.app} o un subdominio suyo (con o sin puerto y barra final).
	 */
	static boolean esComodinAmplioVercel(String origen) {
		if (origen == null || !origen.contains("*")) {
			return false;
		}
		String sinEsquema = origen.toLowerCase(Locale.ROOT)
				.replaceFirst("^https?://", "")
				.replaceAll("/+$", "");
		String host = sinEsquema.split(":", 2)[0];
		return host.equals("vercel.app") || host.endsWith(".vercel.app");
	}

	/** Visible para pruebas unitarias; devuelve una copia. */
	String[] origenesParaPruebas() {
		return origenes.clone();
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOriginPatterns(origenes)
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
				.allowedHeaders("*")
				.exposedHeaders("Location")
				.allowCredentials(false)
				.maxAge(3600);
	}
}
