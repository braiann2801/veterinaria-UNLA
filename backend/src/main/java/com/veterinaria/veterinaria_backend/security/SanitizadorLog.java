package com.veterinaria.veterinaria_backend.security;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Sanitiza identificadores (usuarios, IPs, URLs) antes de escribirlos en los logs.
 *
 * <p>Protecciones:</p>
 * <ul>
 *   <li>Anti-Log Injection: neutraliza saltos de línea (\r, \n) y caracteres de control
 *       evitando que un atacante inyecte líneas falsas en el log.</li>
 *   <li>Anti-PII: enmascara nombres de usuario, emails y direcciones IP para no
 *       exponer datos personales en texto claro.</li>
 * </ul>
 */
public final class SanitizadorLog {

	private static final Pattern CARACTERES_CONTROL = Pattern.compile("[\\r\\n\\t\\p{Cntrl}]");
	private static final int MAX_LONGITUD_TEXTO = 64;

	private SanitizadorLog() {
	}

	/**
	 * Limpia un texto eliminando saltos de línea y caracteres de control para evitar log injection.
	 */
	public static String limpiarControl(String texto) {
		if (texto == null) {
			return "";
		}
		String limpio = CARACTERES_CONTROL.matcher(texto).replaceAll("").trim();
		if (limpio.length() > MAX_LONGITUD_TEXTO) {
			limpio = limpio.substring(0, MAX_LONGITUD_TEXTO) + "...";
		}
		return limpio;
	}

	/**
	 * Sanitiza un nombre de usuario o email para logs, evitando log injection y enmascarando PII.
	 */
	public static String sanitizarUsuario(String username) {
		if (username == null || username.isBlank()) {
			return "anonimo";
		}
		String limpio = limpiarControl(username);
		if (limpio.contains("@")) {
			int arroba = limpio.indexOf('@');
			String local = limpio.substring(0, arroba);
			String dominio = limpio.substring(arroba); // incluye el '@'
			if (local.length() <= 2) {
				return "*" + dominio;
			}
			return local.charAt(0) + "***" + local.charAt(local.length() - 1) + dominio;
		}

		if (limpio.length() <= 2) {
			return "**";
		}
		return limpio.charAt(0) + "***" + limpio.charAt(limpio.length() - 1);
	}

	/**
	 * Sanitiza una dirección IP (IPv4 o IPv6), enmascarando el último segmento para logs.
	 */
	public static String sanitizarIp(String ip) {
		if (ip == null || ip.isBlank()) {
			return "desconocida";
		}
		String limpia = limpiarControl(ip);
		// IPv4: ej 192.168.1.100 -> 192.168.1.***
		int ultimoPunto = limpia.lastIndexOf('.');
		if (ultimoPunto > 0 && limpia.indexOf('.') != ultimoPunto) {
			return limpia.substring(0, ultimoPunto) + ".***";
		}
		// IPv6: ej 2001:db8::1 -> 2001:db8::*** o ::1 -> ::***
		int ultimoDosPuntos = limpia.lastIndexOf(':');
		if (ultimoDosPuntos >= 0) {
			return limpia.substring(0, ultimoDosPuntos + 1) + "***";
		}
		return limpia.length() > 3 ? limpia.substring(0, 3) + "***" : "***";
	}

	/**
	 * Sanitiza una clave compuesta (ej: "login:par:ana|10.0.0.1" o "ana|10.0.0.1").
	 */
	public static String sanitizarParaLog(String clave) {
		if (clave == null || clave.isBlank()) {
			return "vacia";
		}
		String limpia = limpiarControl(clave);
		int barra = limpia.indexOf('|');
		if (barra >= 0) {
			String usuarioOClave = limpia.substring(0, barra);
			String ip = limpia.substring(barra + 1);
			return sanitizarUsuario(usuarioOClave) + "|" + sanitizarIp(ip);
		}
		return sanitizarUsuario(limpia);
	}

	/**
	 * Oculta contraseñas en URLs de conexión (ej: redis://:secret@host:6379).
	 */
	public static String sanitizarUrl(String url) {
		if (url == null || url.isBlank()) {
			return "";
		}
		// Se eliminan los caracteres de control ANTES de extraer el userInfo: si se
		// aplicara el reemplazo sobre el texto original, una credencial con \r o \n
		// inyectada no coincidiria y quedaria expuesta en el log.
		String sinControl = CARACTERES_CONTROL.matcher(url).replaceAll("");
		try {
			URI uri = URI.create(sinControl);
			String userInfo = uri.getUserInfo();
			if (userInfo != null && !userInfo.isBlank()) {
				return limpiarControl(sinControl.replace(userInfo, "***"));
			}
			return limpiarControl(sinControl);
		} catch (Exception ex) {
			return "***";
		}
	}
}
