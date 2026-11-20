package com.veterinaria.veterinaria_backend.security;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementación de {@link AlmacenRateLimit} con Redis (almacén compartido en producción).
 *
 * <p>Diseño agnóstico al hosting:</p>
 * <ul>
 *   <li>Sin dependencias Maven nuevas: cliente RESP mínimo sobre {@link Socket}.</li>
 *   <li>Soporta URIs estándar {@code redis://} y {@code rediss://} (con TLS/SSL)
 *       inyectadas vía {@code REDIS_URL} (Render, Railway, Upstash, Aiven, etc.).</li>
 *   <li>Alta disponibilidad con fallback transparente a memoria ante caídas transitorias de Redis.</li>
 *   <li>Las operaciones de conteo y bloqueo se ejecutan como scripts Lua atómicos
 *       para que varias réplicas no se pisen al contar intentos.</li>
 * </ul>
 */
public class AlmacenRateLimitRedis implements AlmacenRateLimit {

	private static final Logger log = LoggerFactory.getLogger(AlmacenRateLimitRedis.class);
	private static final int TIMEOUT_MS = 2000;
	private static final String PREFIJO = "vet:rl:";
	private static final int PUERTO_DEFAULT = 6379;

	/** TTL, INCR y EXPIRE en una sola operación atómica del servidor Redis. */
	private static final String SCRIPT_VERIFICAR =
			"local bloq = redis.call('TTL', KEYS[1]); "
			+ "if bloq > 0 then return {0, bloq} end; "
			+ "local n = redis.call('INCR', KEYS[2]); "
			+ "if n == 1 then redis.call('EXPIRE', KEYS[2], ARGV[1]) end; "
			+ "if n > tonumber(ARGV[2]) then "
			+ "  local t = redis.call('TTL', KEYS[2]); "
			+ "  return {0, t > 0 and t or tonumber(ARGV[1])} "
			+ "end; "
			+ "return {1, 0}";

	/** GET y SET condicional del bloqueo en una sola operación atómica. */
	private static final String SCRIPT_REGISTRAR_FALLO =
			"local n = tonumber(redis.call('GET', KEYS[1]) or '0'); "
			+ "if n >= tonumber(ARGV[1]) then "
			+ "  redis.call('SET', KEYS[2], '1', 'EX', ARGV[2]); "
			+ "  return 1 "
			+ "end; "
			+ "return 0";

	private final String host;
	private final int port;
	private final String password;
	private final boolean ssl;
	private final Integer database;
	private final AlmacenRateLimitMemoria fallbackMemoria;

	public AlmacenRateLimitRedis(String redisUrl, Clock reloj) {
		this.fallbackMemoria = new AlmacenRateLimitMemoria(reloj);
		ConfiguracionConexion cfg = parsearUrl(redisUrl);
		this.host = cfg.host();
		this.port = cfg.port();
		this.password = cfg.password();
		this.ssl = cfg.ssl();
		this.database = cfg.database();
	}

	@Override
	public ResultadoRateLimit verificarYConsumir(String clave, int maxIntentos, Duration ventana) {
		String claveBloqueo = PREFIJO + "bloq:" + clave;
		String claveIntentos = PREFIJO + "int:" + clave;
		long ventanaSegundos = Math.max(1, ventana.toSeconds());

		try {
			Object[] respuesta = (Object[]) ejecutarScript(SCRIPT_VERIFICAR, 2,
					claveBloqueo, claveIntentos, String.valueOf(ventanaSegundos), String.valueOf(maxIntentos));
			long permitido = ((Number) respuesta[0]).longValue();
			long segundos = ((Number) respuesta[1]).longValue();
			return permitido == 1 ? ResultadoRateLimit.concedido() : ResultadoRateLimit.bloqueado(segundos);
		} catch (Exception ex) {
			log.warn("Error comunicando con Redis en verificarYConsumir, aplicando fallback en memoria: {}", ex.getMessage());
			return fallbackMemoria.verificarYConsumir(clave, maxIntentos, ventana);
		}
	}

	@Override
	public boolean registrarFallo(String clave, int maxIntentos, Duration ventana) {
		String claveBloqueo = PREFIJO + "bloq:" + clave;
		String claveIntentos = PREFIJO + "int:" + clave;
		long ventanaSegundos = Math.max(1, ventana.toSeconds());

		try {
			Object respuesta = ejecutarScript(SCRIPT_REGISTRAR_FALLO, 2,
					claveIntentos, claveBloqueo, String.valueOf(maxIntentos), String.valueOf(ventanaSegundos));
			return ((Number) respuesta).longValue() == 1;
		} catch (Exception ex) {
			log.warn("Error comunicando con Redis en registrarFallo, aplicando fallback en memoria: {}", ex.getMessage());
			return fallbackMemoria.registrarFallo(clave, maxIntentos, ventana);
		}
	}

	@Override
	public void registrarExito(String clave) {
		String claveBloqueo = PREFIJO + "bloq:" + clave;
		String claveIntentos = PREFIJO + "int:" + clave;
		try {
			ejecutarComandoObject("DEL", claveIntentos, claveBloqueo);
		} catch (Exception ex) {
			log.warn("Error comunicando con Redis en registrarExito: {}", ex.getMessage());
		}
		fallbackMemoria.registrarExito(clave);
	}

	@Override
	public long obtenerSegundosBloqueo(String clave) {
		String claveBloqueo = PREFIJO + "bloq:" + clave;
		try {
			Object respuesta = ejecutarComandoObject("TTL", claveBloqueo);
			long ttl = respuesta instanceof Number n ? n.longValue() : 0L;
			return ttl > 0 ? ttl : 0;
		} catch (Exception ex) {
			return fallbackMemoria.obtenerSegundosBloqueo(clave);
		}
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public boolean isSsl() {
		return ssl;
	}

	private Socket crearSocket() throws IOException {
		Socket socket;
		if (ssl) {
			socket = SSLSocketFactory.getDefault().createSocket();
		} else {
			socket = new Socket();
		}
		socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
		socket.setSoTimeout(TIMEOUT_MS);
		return socket;
	}

	/** Ejecuta un script Lua con {@code numKeys} primeros argumentos como KEYS. */
	private Object ejecutarScript(String script, int numKeys, String... args) throws IOException {
		String[] comando = new String[args.length + 3];
		comando[0] = "EVAL";
		comando[1] = script;
		comando[2] = String.valueOf(numKeys);
		System.arraycopy(args, 0, comando, 3, args.length);
		return ejecutarComandoObject(comando);
	}

	private Object ejecutarComandoObject(String... args) throws IOException {
		try (Socket socket = crearSocket();
				OutputStream out = new BufferedOutputStream(socket.getOutputStream());
				InputStream in = new BufferedInputStream(socket.getInputStream())) {

			autenticarYSeleccionarDbSiAplica(out, in);
			enviarComandoResp(out, args);
			return leerRespuestaResp(in);
		}
	}

	private void autenticarYSeleccionarDbSiAplica(OutputStream out, InputStream in) throws IOException {
		if (password != null && !password.isBlank()) {
			enviarComandoResp(out, "AUTH", password);
			leerRespuestaResp(in);
		}
		if (database != null) {
			enviarComandoResp(out, "SELECT", String.valueOf(database));
			leerRespuestaResp(in);
		}
	}

	private static void enviarComandoResp(OutputStream out, String... args) throws IOException {
		out.write('*');
		out.write(Integer.toString(args.length).getBytes(StandardCharsets.UTF_8));
		out.write('\r');
		out.write('\n');
		for (String arg : args) {
			byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
			out.write('$');
			out.write(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
			out.write('\r');
			out.write('\n');
			out.write(bytes);
			out.write('\r');
			out.write('\n');
		}
		out.flush();
	}

	private static Object leerRespuestaResp(InputStream in) throws IOException {
		int primerByte = in.read();
		if (primerByte == -1) {
			throw new IOException("Conexión cerrada por el servidor Redis");
		}
		switch (primerByte) {
			case '+': // Simple string
				return leerLinea(in);
			case '-': // Error
				String err = leerLinea(in);
				throw new IOException("Redis error: " + err);
			case ':': // Integer
				return Long.parseLong(leerLinea(in));
			case '$': // Bulk string
				int longitud = Integer.parseInt(leerLinea(in));
				if (longitud == -1) {
					return null;
				}
				byte[] datos = in.readNBytes(longitud);
				in.read(); // '\r'
				in.read(); // '\n'
				return new String(datos, StandardCharsets.UTF_8);
			case '*': // Array
				int elementos = Integer.parseInt(leerLinea(in));
				if (elementos == -1) {
					return null;
				}
				Object[] arr = new Object[elementos];
				for (int i = 0; i < elementos; i++) {
					arr[i] = leerRespuestaResp(in);
				}
				return arr;
			default:
				throw new IOException("Formato RESP desconocido: " + (char) primerByte);
		}
	}

	private static String leerLinea(InputStream in) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		int b;
		while ((b = in.read()) != -1) {
			if (b == '\r') {
				int next = in.read();
				if (next == '\n') {
					break;
				}
				buffer.write(b);
				if (next != -1) {
					buffer.write(next);
				}
			} else {
				buffer.write(b);
			}
		}
		return buffer.toString(StandardCharsets.UTF_8);
	}

	static ConfiguracionConexion parsearUrl(String redisUrl) {
		if (redisUrl == null || redisUrl.isBlank()) {
			return new ConfiguracionConexion("localhost", PUERTO_DEFAULT, null, false, null);
		}
		try {
			URI uri = URI.create(redisUrl.trim());
			boolean ssl = "rediss".equalsIgnoreCase(uri.getScheme());
			String host = uri.getHost() != null ? uri.getHost() : "localhost";
			// rediss:// usa el mismo puerto estándar (6379) con TLS; algunos proveedores
			// exponen 6380, pero se especifica explícitamente en la URL cuando es así.
			int port = uri.getPort() > 0 ? uri.getPort() : PUERTO_DEFAULT;
			String password = null;
			if (uri.getUserInfo() != null) {
				String userInfo = uri.getUserInfo();
				int dosPuntos = userInfo.indexOf(':');
				password = dosPuntos >= 0 ? userInfo.substring(dosPuntos + 1) : userInfo;
			}
			Integer database = null;
			String path = uri.getPath();
			if (path != null && path.length() > 1) {
				try {
					database = Integer.parseInt(path.substring(1));
				} catch (NumberFormatException ignored) {
				}
			}
			return new ConfiguracionConexion(host, port, password, ssl, database);
		} catch (Exception ex) {
			log.warn("No se pudo parsear REDIS_URL '{}', usando defaults locales: {}",
					SanitizadorLog.sanitizarUrl(redisUrl), ex.getMessage());
			return new ConfiguracionConexion("localhost", PUERTO_DEFAULT, null, false, null);
		}
	}

	record ConfiguracionConexion(String host, int port, String password, boolean ssl, Integer database) {
	}
}
