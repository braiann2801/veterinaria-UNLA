package com.veterinaria.veterinaria_backend.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Supplier;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

/**
 * Lee un {@code LocalDateTime} aceptando tambien los formatos que emite un
 * formulario web.
 *
 * <p><b>Por que existe.</b> El frontend manda la fecha desde un
 * {@code <input type="date">}, y ese control entrega {@code YYYY-MM-DD} a secas,
 * sin hora. Jackson, con el comportamiento por defecto de
 * {@code LocalDateTimeDeserializer}, exige la componente horaria y responde
 * {@code 400} con un {@code DateTimeParseException}. Se reprodujo contra la base:
 * {@code fechaAtencion: "2026-10-01"} devolvia
 * {@code "Text '2026-10-01' could not be parsed at index 10"}, o sea que la
 * pantalla de ficha medica no podia guardar ninguna atencion.</p>
 *
 * <p><b>Por que no es un {@code @JsonFormat}.</b> El patron con secciones
 * opcionales {@code [yyyy-MM-dd'T'HH:mm:ss][yyyy-MM-dd'T'HH:mm][yyyy-MM-dd]} se
 * descarto a proposito: las tres secciones son opcionales, asi que el
 * {@code DateTimeFormatter} acepta {@code 2026-10-01}, pero despues
 * {@code LocalDateTime.from(...)} no encuentra componente horario y vuelve a
 * tirar. El error no se corrige, se corre de lugar. Una cadena de intentos
 * explicitos si resuelve el caso y ademas deja un mensaje util.</p>
 *
 * <p><b>Formatos aceptados, en orden de intento.</b></p>
 * <ol>
 *   <li>{@code 2026-10-01T09:00}, {@code 2026-10-01T09:00:00},
 *       {@code 2026-10-01T09:00:00.000Z}, {@code 2026-10-01T09:00:00-03:00}:
 *       ISO con offset opcional. El offset se ignora, porque la columna es
 *       {@code datetime} sin zona y el dominio trabaja en hora local.</li>
 *   <li>{@code 2026-10-01 09:00:00}: lo mismo con espacio en vez de {@code T}.
 *       No es ISO, pero es el error de tipeo mas comun y rechazarlo obligaria al
 *       cliente a construir la cadena a mano.</li>
 *   <li>{@code 2026-10-01}: solo la fecha, que se completa a las 00:00:00. Es el
 *       formato de {@code <input type="date">}.</li>
 * </ol>
 *
 * <p><b>Lo que no hace.</b> No cambia la regla de negocio de las fechas
 * retroactivas: sigue sin haber ningun {@code @Future} en el path de turnos, asi
 * que esta clase no tiene opinion sobre pasado ni futuro.</p>
 */
public class FlexibleLocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {

	/** Sin segundos, con segundos, con fraccion y con offset: todo lo acepta. */
	private static final DateTimeFormatter CON_HORA = DateTimeFormatter.ISO_DATE_TIME;

	/** El mismo formato con espacio en vez de la T: "2026-10-01 09:00:00". */
	private static final DateTimeFormatter CON_HORA_ESPACIO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	@Override
	public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
		if (p.hasToken(JsonToken.VALUE_NULL)) {
			return null;
		}
		if (!p.hasToken(JsonToken.VALUE_STRING)) {
			// Numeros, objetos o arrays no son fechas: que lo diga Jackson con su
			// propio mensaje, en vez de inventar una conversion silenciosa.
			return (LocalDateTime) ctxt.handleUnexpectedToken(LocalDateTime.class, p);
		}

		String crudo = p.getString();
		if (crudo == null) {
			return null;
		}
		// `final` por necesidad tecnica, no por estilo: los intentos de abajo son
		// lambdas y una variable reasignada no puede capturarse.
		final String texto = crudo.trim();
		// Cadena vacia se trata como ausente, igual que el comportamiento por
		// defecto: un formulario manda "" cuando el operador limpia el campo, y en
		// `fechaAtencion` (opcional) vacio significa "no lo digas".
		if (texto.isEmpty()) {
			return null;
		}

		LocalDateTime conHora = intentar(() -> LocalDateTime.parse(texto, CON_HORA));
		if (conHora != null) {
			return conHora;
		}

		LocalDateTime conEspacio = intentar(() -> LocalDateTime.parse(texto, CON_HORA_ESPACIO));
		if (conEspacio != null) {
			return conEspacio;
		}

		LocalDate soloFecha = intentarFecha(() -> LocalDate.parse(texto, DateTimeFormatter.ISO_LOCAL_DATE));
		if (soloFecha != null) {
			return soloFecha.atStartOfDay();
		}

		// Ningun formato sirvio. Se informa que formatos se aceptan en vez de dejar
		// el "could not be parsed at index N" de Jackson, que no le dice nada util a
		// quien esta tipeando el dato.
		throw new InvalidFormatException(p,
				"Se esperaba 'YYYY-MM-DDTHH:mm:ss', 'YYYY-MM-DDTHH:mm' o 'YYYY-MM-DD'",
				texto, LocalDateTime.class);
	}

	/**
	 * Intenta el parseo y devuelve {@code null} si el formato no corresponde.
	 *
	 * <p>Devolver {@code null} en vez de propagar la excepcion es lo que hace
	 * posible la cadena de intentos: el error de verdad solo se reporta cuando
	 * fallaron todos.</p>
	 */
	private static <T> T intentar(Supplier<T> parseo) {
		try {
			return parseo.get();
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	/**
	 * Adaptador para los formatos que no producen un {@code LocalDateTime}.
	 *
	 * <p>El parseo de {@link LocalDate} devuelve {@code LocalDate}, asi que se
	 * lleva por el mismo helper con otro tipo de retorno.</p>
	 */
	private static LocalDate intentarFecha(Supplier<LocalDate> parseo) {
		return intentar(parseo);
	}
}