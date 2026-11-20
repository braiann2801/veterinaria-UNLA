package com.veterinaria.veterinaria_backend.security;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.veterinaria.veterinaria_backend.exception.DemasiadosIntentosException;

/**
 * Control de tasa de intentos de autenticación (Rate Limiting).
 *
 * <p>Protege los puntos de entrada sensibles:</p>
 * <ul>
 *   <li><b>Login por par (usuario, IP):</b> previene fuerza bruta contra credenciales de un empleado.</li>
 *   <li><b>Login agregado por IP (anti-password spraying):</b> bloquea la IP si intenta muchas cuentas distintas.</li>
 *   <li><b>Login agregado por usuario (anti-ataque distribuido):</b> bloquea la cuenta si recibe intentos de muchas IPs.</li>
 *   <li><b>Cambio de contraseña (usuario autenticado / IP):</b> evita fuerza bruta sobre la contraseña actual.</li>
 * </ul>
 *
 * <p>El almacenamiento está desacoplado vía {@link AlmacenRateLimit} (en memoria por defecto para local,
 * o Redis para entornos distribuidos).</p>
 */
@Component
public class LimitadorIntentosLogin implements RateLimiterService {

	private static final Logger log = LoggerFactory.getLogger(LimitadorIntentosLogin.class);

	private final AlmacenRateLimit almacen;
	private final int maxPar;
	private final Duration ventanaPar;
	private final int maxIp;
	private final Duration ventanaIp;
	private final int maxUsuario;
	private final Duration ventanaUsuario;
	private final int maxCambioPassword;
	private final Duration ventanaCambioPassword;

	@Autowired
	public LimitadorIntentosLogin(
			AlmacenRateLimit almacen,
			@Value("${veterinaria.auth.login-intentos-maximos:5}") int maxPar,
			@Value("${veterinaria.auth.login-bloqueo-minutos:15}") long minutosPar,
			@Value("${veterinaria.auth.login-ip-intentos-maximos:20}") int maxIp,
			@Value("${veterinaria.auth.login-ip-bloqueo-minutos:15}") long minutosIp,
			@Value("${veterinaria.auth.login-usuario-intentos-maximos:15}") int maxUsuario,
			@Value("${veterinaria.auth.login-usuario-bloqueo-minutos:15}") long minutosUsuario,
			@Value("${veterinaria.auth.cambio-password-intentos-maximos:5}") int maxCambioPassword,
			@Value("${veterinaria.auth.cambio-password-bloqueo-minutos:15}") long minutosCambioPassword) {
		if (maxPar < 1 || minutosPar < 1 || maxIp < 1 || minutosIp < 1
				|| maxUsuario < 1 || minutosUsuario < 1
				|| maxCambioPassword < 1 || minutosCambioPassword < 1) {
			throw new IllegalStateException("login-intentos-maximos y login-bloqueo-minutos deben ser al menos 1");
		}
		this.almacen = almacen;
		this.maxPar = maxPar;
		this.ventanaPar = Duration.ofMinutes(minutosPar);
		this.maxIp = maxIp;
		this.ventanaIp = Duration.ofMinutes(minutosIp);
		this.maxUsuario = maxUsuario;
		this.ventanaUsuario = Duration.ofMinutes(minutosUsuario);
		this.maxCambioPassword = maxCambioPassword;
		this.ventanaCambioPassword = Duration.ofMinutes(minutosCambioPassword);
	}

	/** Constructor de conveniencia para tests directos en memoria. */
	public LimitadorIntentosLogin(Clock reloj, int intentosMaximos, long minutosDeBloqueo) {
		this(new AlmacenRateLimitMemoria(reloj), intentosMaximos, minutosDeBloqueo,
				20, 15, 15, 15, 5, 15);
	}

	/** Constructor de conveniencia para tests de reglas avanzadas. */
	public LimitadorIntentosLogin(Clock reloj, int maxPar, long minPar, int maxIp, long minIp,
			int maxUsuario, long minUsuario, int maxCambioPw, long minCambioPw) {
		this(new AlmacenRateLimitMemoria(reloj), maxPar, minPar, maxIp, minIp, maxUsuario, minUsuario, maxCambioPw, minCambioPw);
	}

	/** Clave de par usuario e IP. */
	public static String clave(String usernameNormalizado, String ip) {
		return usernameNormalizado + "|" + ip;
	}

	public static String claveIp(String ip) {
		return "ip:" + ip;
	}

	public static String claveUsuario(String usernameNormalizado) {
		return "usr:" + usernameNormalizado;
	}

	public static String claveCambioPassword(Long usuarioId, String ip) {
		return "cpw:" + usuarioId + "|" + ip;
	}

	// ------------------------------------------------------------------ Login

	@Override
	public void verificarLogin(String usernameNormalizado, String ip) {
		String cPar = clave(usernameNormalizado, ip);
		String cIp = claveIp(ip);
		String cUsr = claveUsuario(usernameNormalizado);

		// Si alguna regla ya está en período de bloqueo activo, rechazamos inmediatamente sin consumir cupo
		long bPar = almacen.obtenerSegundosBloqueo(cPar);
		long bIp = almacen.obtenerSegundosBloqueo(cIp);
		long bUsr = almacen.obtenerSegundosBloqueo(cUsr);
		long maxBloqueo = Math.max(bPar, Math.max(bIp, bUsr));
		if (maxBloqueo > 0) {
			throw new DemasiadosIntentosException(maxBloqueo);
		}

		// Reservamos atómicamente el cupo en las tres dimensiones
		ResultadoRateLimit rPar = almacen.verificarYConsumir(cPar, maxPar, ventanaPar);
		ResultadoRateLimit rIp = almacen.verificarYConsumir(cIp, maxIp, ventanaIp);
		ResultadoRateLimit rUsr = almacen.verificarYConsumir(cUsr, maxUsuario, ventanaUsuario);

		if (!rPar.permitido() || !rIp.permitido() || !rUsr.permitido()) {
			long espera = Math.max(rPar.segundosParaReintentar(),
					Math.max(rIp.segundosParaReintentar(), rUsr.segundosParaReintentar()));
			throw new DemasiadosIntentosException(espera);
		}
	}

	@Override
	public void registrarFalloLogin(String usernameNormalizado, String ip) {
		String cPar = clave(usernameNormalizado, ip);
		String cIp = claveIp(ip);
		String cUsr = claveUsuario(usernameNormalizado);

		boolean bloqPar = almacen.registrarFallo(cPar, maxPar, ventanaPar);
		boolean bloqIp = almacen.registrarFallo(cIp, maxIp, ventanaIp);
		boolean bloqUsr = almacen.registrarFallo(cUsr, maxUsuario, ventanaUsuario);

		if (bloqPar) {
			log.warn("Login bloqueado {} min por {} intentos fallidos en par usuario/IP: {}",
					ventanaPar.toMinutes(), maxPar, SanitizadorLog.sanitizarParaLog(cPar));
		}
		if (bloqIp) {
			log.warn("IP bloqueada {} min por {} intentos fallidos (anti-password spraying): {}",
					ventanaIp.toMinutes(), maxIp, SanitizadorLog.sanitizarIp(ip));
		}
		if (bloqUsr) {
			log.warn("Usuario bloqueado {} min por {} intentos fallidos (anti-ataque distribuido): {}",
					ventanaUsuario.toMinutes(), maxUsuario, SanitizadorLog.sanitizarUsuario(usernameNormalizado));
		}
	}

	@Override
	public void registrarExitoLogin(String usernameNormalizado, String ip) {
		almacen.registrarExito(clave(usernameNormalizado, ip));
		almacen.registrarExito(claveUsuario(usernameNormalizado));
	}

	// -------------------------------------------------------- Cambio Password

	@Override
	public void verificarCambioPassword(Long usuarioId, String ip) {
		String claveCp = claveCambioPassword(usuarioId, ip);
		long bloqueado = almacen.obtenerSegundosBloqueo(claveCp);
		if (bloqueado > 0) {
			throw new DemasiadosIntentosException(bloqueado);
		}
		ResultadoRateLimit res = almacen.verificarYConsumir(claveCp, maxCambioPassword, ventanaCambioPassword);
		if (!res.permitido()) {
			throw new DemasiadosIntentosException(res.segundosParaReintentar());
		}
	}

	@Override
	public void registrarFalloCambioPassword(Long usuarioId, String ip) {
		String claveCp = claveCambioPassword(usuarioId, ip);
		boolean bloqueado = almacen.registrarFallo(claveCp, maxCambioPassword, ventanaCambioPassword);
		if (bloqueado) {
			log.warn("Cambio de contraseña bloqueado {} min por {} intentos fallidos para usuarioId {} desde IP {}",
					ventanaCambioPassword.toMinutes(), maxCambioPassword, usuarioId, SanitizadorLog.sanitizarIp(ip));
		}
	}

	@Override
	public void registrarExitoCambioPassword(Long usuarioId, String ip) {
		almacen.registrarExito(claveCambioPassword(usuarioId, ip));
	}

	// ------------------------------------------------ Compatibilidad anterior

	public void verificar(String clave) {
		long bloqueado = almacen.obtenerSegundosBloqueo(clave);
		if (bloqueado > 0) {
			throw new DemasiadosIntentosException(bloqueado);
		}
		ResultadoRateLimit res = almacen.verificarYConsumir(clave, maxPar, ventanaPar);
		if (!res.permitido()) {
			throw new DemasiadosIntentosException(res.segundosParaReintentar());
		}
	}

	public void registrarFallo(String clave) {
		boolean bloqueado = almacen.registrarFallo(clave, maxPar, ventanaPar);
		if (bloqueado) {
			log.warn("Login bloqueado {} min por {} intentos fallidos: {}",
					ventanaPar.toMinutes(), maxPar, SanitizadorLog.sanitizarParaLog(clave));
		}
	}

	public void registrarExito(String clave) {
		almacen.registrarExito(clave);
	}
}
