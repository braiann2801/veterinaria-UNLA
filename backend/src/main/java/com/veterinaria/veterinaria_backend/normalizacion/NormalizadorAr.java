package com.veterinaria.veterinaria_backend.normalizacion;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizacion de datos personales segun el uso argentino.
 *
 * <p>Se aplica en los constructores de los DTO de entrada, antes de Bean
 * Validation: asi las anotaciones validan el valor ya normalizado y el
 * mostrador puede escribir "30.123.456" o "+54 9 11 2345-6789" sin que el alta
 * se rechace por el formato.</p>
 *
 * <p>Solo se quitan separadores, nunca letras: un DNI "abc" sigue llegando
 * como "abc" y la validacion lo rechaza con su mensaje, en vez de convertirse
 * en un campo vacio que pasa en silencio.</p>
 */
public final class NormalizadorAr {

	private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

	/** Particulas de apellidos y nombres compuestos que van en minuscula salvo al inicio. */
	private static final Set<String> PARTICULAS =
			Set.of("de", "del", "la", "las", "los", "y", "e", "da", "di", "van", "von");

	private static final Pattern SEPARADORES_DNI = Pattern.compile("[.\\s-]");
	private static final Pattern SEPARADORES_TELEFONO = Pattern.compile("[.\\s()/-]");
	private static final Pattern SOLO_DIGITOS = Pattern.compile("\\d+");
	private static final Pattern PREFIJO_Y_NUMERO = Pattern.compile("^([A-Z]+)\\s*(\\d+)$");

	private NormalizadorAr() {
	}

	/**
	 * Mayuscula inicial en cada palabra ("juan carlos" -> "Juan Carlos",
	 * "JUAN PEREZ" -> "Juan Perez"), tambien despues de guion o apostrofo
	 * ("o'connor" -> "O'Connor"). Las particulas quedan en minuscula salvo que
	 * abran el nombre ("de la fuente" -> "De la Fuente").
	 *
	 * <p>Un valor en blanco se devuelve vacio y no {@code null}, para que
	 * {@code @NotBlank} siga avisando que el campo es obligatorio.</p>
	 */
	public static String nombrePropio(String valor) {
		if (valor == null) {
			return null;
		}
		String limpio = valor.trim().replaceAll("\\s+", " ").toLowerCase(ES_AR);
		if (limpio.isEmpty()) {
			return limpio;
		}
		String[] palabras = limpio.split(" ");
		StringBuilder resultado = new StringBuilder();
		for (int i = 0; i < palabras.length; i++) {
			if (i > 0) {
				resultado.append(' ');
			}
			String palabra = palabras[i];
			resultado.append(i > 0 && PARTICULAS.contains(palabra) ? palabra : capitalizarPartes(palabra));
		}
		return resultado.toString();
	}

	/** DNI solo con digitos: "30.123.456" -> "30123456". En blanco es {@code null}. */
	public static String dni(String valor) {
		if (valor == null) {
			return null;
		}
		String limpio = SEPARADORES_DNI.matcher(valor).replaceAll("");
		return limpio.isEmpty() ? null : limpio;
	}

	/**
	 * Numero nacional de 10 digitos (codigo de area + abonado, plan de
	 * numeracion de ENACOM): se quitan separadores, el "+" o "00" internacional,
	 * el 54 de pais, el 9 de celular y el 0 de larga distancia.
	 *
	 * <p>El 15 de celular no se quita: depende del largo del codigo de area y
	 * sin la tabla de codigos no hay forma segura de saber donde esta. El numero
	 * queda con 12 digitos y la validacion pide cargarlo sin el 15.</p>
	 */
	public static String telefono(String valor) {
		if (valor == null) {
			return null;
		}
		String limpio = SEPARADORES_TELEFONO.matcher(valor).replaceAll("");
		if (limpio.startsWith("+")) {
			limpio = limpio.substring(1);
		}
		if (limpio.isEmpty()) {
			return null;
		}
		if (!SOLO_DIGITOS.matcher(limpio).matches()) {
			return limpio;
		}
		if (limpio.startsWith("00")) {
			limpio = limpio.substring(2);
		}
		// Ningun codigo de area argentino empieza con 5: un 54 al inicio es el pais.
		if (limpio.startsWith("54") && (limpio.length() == 12 || limpio.length() == 13)) {
			limpio = limpio.substring(2);
			if (limpio.length() == 11 && limpio.startsWith("9")) {
				limpio = limpio.substring(1);
			}
		} else if (limpio.length() == 11 && limpio.startsWith("0")) {
			limpio = limpio.substring(1);
		}
		return limpio;
	}

	/**
	 * Matricula en mayusculas, sin puntos y con los espacios colapsados. Si es un
	 * prefijo de letras seguido de un numero se separa con un espacio
	 * ("m.p. 1234" y "mp1234" -> "MP 1234"); cualquier otro formato conserva sus
	 * guiones ("mp-abm-9" -> "MP-ABM-9"). No se inventa un prefijo: cada colegio
	 * provincial tiene el suyo.
	 */
	public static String matricula(String valor) {
		if (valor == null) {
			return null;
		}
		String limpio = valor.trim().toUpperCase(Locale.ROOT).replace(".", "").replaceAll("\\s+", " ");
		if (limpio.isEmpty()) {
			return null;
		}
		Matcher prefijo = PREFIJO_Y_NUMERO.matcher(limpio);
		return prefijo.matches() ? prefijo.group(1) + " " + prefijo.group(2) : limpio;
	}

	/** Email recortado y en minuscula. En blanco es {@code null}. */
	public static String email(String valor) {
		if (valor == null) {
			return null;
		}
		String limpio = valor.trim().toLowerCase(Locale.ROOT);
		return limpio.isEmpty() ? null : limpio;
	}

	/** Texto libre (p. ej. la direccion): recortado y sin espacios repetidos. En blanco es {@code null}. */
	public static String texto(String valor) {
		if (valor == null) {
			return null;
		}
		String limpio = valor.trim().replaceAll("\\s+", " ");
		return limpio.isEmpty() ? null : limpio;
	}

	/** Mayuscula inicial en la palabra y en cada parte despues de un guion o apostrofo. */
	private static String capitalizarPartes(String palabra) {
		StringBuilder resultado = new StringBuilder(palabra.length());
		boolean inicio = true;
		for (int i = 0; i < palabra.length(); ) {
			int codigo = palabra.codePointAt(i);
			resultado.appendCodePoint(inicio ? Character.toUpperCase(codigo) : codigo);
			inicio = codigo == '-' || codigo == '\'';
			i += Character.charCount(codigo);
		}
		return resultado.toString();
	}
}
