package com.veterinaria.veterinaria_backend.importacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.ReservaGuarderia;
import com.veterinaria.veterinaria_backend.normalizacion.NormalizadorAr;

/**
 * Parsers tolerantes para el Excel historico de Huellitas.
 *
 * <p>La planilla no tiene un formato unico: conviven fechas {@code dd/MM/yyyy},
 * {@code yyyy-MM-dd}, {@code dd/MM/yy} y textos como {@code "12 de Mayo 2026"},
 * montos con simbolo, separador de miles y coma decimal, telefonos escritos de
 * cinco maneras distintas y edades en anos, meses o "anos y medio".</p>
 *
 * <p>Todo lo que no se puede interpretar devuelve {@code null}; el constructor
 * del plan lo registra como incidencia en el reporte. No se inventan valores en
 * silencio.</p>
 */
public final class ParseoDatos {

	private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

	private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("d/M/yyyy");
	private static final DateTimeFormatter DMY_CORTO = DateTimeFormatter.ofPattern("d/M/yy");
	private static final DateTimeFormatter DMY_GUION = DateTimeFormatter.ofPattern("d-M-yyyy");

	private static final Pattern FECHA_TEXTO = Pattern.compile(
			"(\\d{1,2})\\s+de\\s+([\\p{L}]+)(?:\\s+de\\s+|\\s+)(\\d{4})",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern HONORIFICO = Pattern.compile("^(?i)\\s*(dr|dra)\\.?\\s+");

	private static final Pattern EDAD = Pattern.compile(
			"(\\d+(?:[.,]\\d+)?)\\s*([\\p{L}]+)");

	private static final Map<String, Integer> MESES = Map.ofEntries(
			Map.entry("enero", 1),
			Map.entry("febrero", 2),
			Map.entry("marzo", 3),
			Map.entry("abril", 4),
			Map.entry("mayo", 5),
			Map.entry("junio", 6),
			Map.entry("julio", 7),
			Map.entry("agosto", 8),
			Map.entry("septiembre", 9),
			Map.entry("setiembre", 9),
			Map.entry("octubre", 10),
			Map.entry("noviembre", 11),
			Map.entry("diciembre", 12));

	private ParseoDatos() {
	}

	/** Fecha en cualquiera de los formatos de la planilla. {@code null} si no se entiende. */
	public static LocalDate fecha(String valor) {
		if (valor == null) {
			return null;
		}
		String limpio = valor.trim();
		if (limpio.isEmpty()) {
			return null;
		}
		for (DateTimeFormatter formato : List.of(ISO, DMY, DMY_CORTO, DMY_GUION)) {
			try {
				return LocalDate.parse(limpio, formato);
			} catch (DateTimeParseException ignorada) {
				// Se prueba el siguiente formato.
			}
		}
		Matcher matcher = FECHA_TEXTO.matcher(limpio);
		if (matcher.matches()) {
			Integer mes = MESES.get(matcher.group(2).toLowerCase(ES_AR));
			if (mes != null) {
				try {
					return LocalDate.of(
							Integer.parseInt(matcher.group(3)),
							mes,
							Integer.parseInt(matcher.group(1)));
				} catch (RuntimeException ignorada) {
					return null;
				}
			}
		}
		return null;
	}

	/**
	 * Monto en pesos. Acepta {@code $ 32.000,00}, {@code 12000}, {@code 32.000}
	 * y {@code 28,5}. Si el separador unico tiene 1 o 2 digitos a la derecha se
	 * interpreta como decimal; si tiene 3, como miles.
	 */
	public static BigDecimal dinero(String valor) {
		return numero(valor);
	}

	/** Peso en kg: mismo parser numerico que el dinero, sin la unidad. */
	public static BigDecimal peso(String valor) {
		return numero(valor);
	}

	private static BigDecimal numero(String valor) {
		if (valor == null) {
			return null;
		}
		String limpio = valor.replaceAll("[^0-9,.-]", "").trim();
		if (limpio.isEmpty() || limpio.equals("-") || limpio.equals(",") || limpio.equals(".")) {
			return null;
		}
		boolean negativo = limpio.startsWith("-");
		limpio = limpio.replace("-", "");
		if (limpio.isEmpty()) {
			return null;
		}

		int ultimaComa = limpio.lastIndexOf(',');
		int ultimoPunto = limpio.lastIndexOf('.');
		String normalizado;
		if (ultimaComa >= 0 && ultimoPunto >= 0) {
			if (ultimaComa > ultimoPunto) {
				normalizado = limpio.replace(".", "").replace(',', '.');
			} else {
				normalizado = limpio.replace(",", "");
			}
		} else if (ultimaComa >= 0) {
			normalizado = esDecimalUnico(limpio, ultimaComa)
					? limpio.replace(',', '.')
					: limpio.replace(",", "");
		} else if (ultimoPunto >= 0) {
			normalizado = esDecimalUnico(limpio, ultimoPunto)
					? limpio
					: limpio.replace(".", "");
		} else {
			normalizado = limpio;
		}

		try {
			BigDecimal resultado = new BigDecimal(normalizado);
			return negativo ? resultado.negate() : resultado;
		} catch (NumberFormatException ignorada) {
			return null;
		}
	}

	private static boolean esDecimalUnico(String valor, int posicionSeparador) {
		int digitosDerecha = valor.length() - posicionSeparador - 1;
		return digitosDerecha >= 1 && digitosDerecha <= 2;
	}

	/**
	 * Telefono nacional de 10 digitos. El Excel trae formatos que el
	 * {@link NormalizadorAr} no resuelve: {@code 15-6677-2233} (sin codigo de
	 * area) y {@code 0341-155443322} (codigo de area + 15).
	 *
	 * @param valor    texto original
	 * @param fallback numero provisional si no hay ningun digito
	 */
	public static String telefono(String valor, String fallback) {
		if (valor == null) {
			return fallback;
		}
		String digitos = valor.replaceAll("[^0-9]", "");
		if (digitos.isEmpty()) {
			return fallback;
		}

		String numero = digitos;
		if (numero.startsWith("00")) {
			numero = numero.substring(2);
		}

		if (numero.startsWith("54") && (numero.length() == 12 || numero.length() == 13)) {
			numero = numero.substring(2);
			if (numero.length() == 11 && numero.startsWith("9")) {
				numero = numero.substring(1);
			}
		} else if (numero.length() == 11 && numero.startsWith("0")) {
			numero = numero.substring(1);
		} else if (numero.length() == 10 && numero.startsWith("15")) {
			numero = "11" + numero.substring(2);
		} else if (numero.length() == 12 && numero.startsWith("0")) {
			numero = quitarCeroYQuince(numero);
		} else if (numero.length() == 13 && numero.startsWith("0")) {
			numero = quitarCeroYQuince(numero);
		}

		return numero;
	}

	/**
	 * Quita el 0 de larga distancia y el 15 de celular cuando estan en la
	 * posicion esperada. El codigo de area argentino tiene entre 2 y 4 digitos,
	 * por eso se busca el 15 despues del area.
	 */
	private static String quitarCeroYQuince(String numero) {
		String sinCero = numero.substring(1);
		int indiceQuince = sinCero.indexOf("15");
		if (indiceQuince >= 2 && indiceQuince <= 4) {
			return sinCero.substring(0, indiceQuince) + sinCero.substring(indiceQuince + 2);
		}
		return sinCero;
	}

	/**
	 * Edad aproximada en meses. {@code "4 años"} -> 48, {@code "2 años y medio"}
	 * -> 30, {@code "6 meses"} -> 6.
	 */
	public static Integer mesesDesdeEdad(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		String limpio = valor.trim().toLowerCase(ES_AR);
		Matcher matcher = EDAD.matcher(limpio);
		if (matcher.find()) {
			double cantidad = Double.parseDouble(matcher.group(1).replace(',', '.'));
			String unidad = matcher.group(2);
			if (unidad.startsWith("mes")) {
				return (int) Math.round(cantidad);
			}
			int meses = (int) Math.round(cantidad * 12);
			if (limpio.contains("medio")) {
				meses += 6;
			}
			return meses;
		}
		try {
			return (int) Math.round(Double.parseDouble(limpio.replace(',', '.')) * 12);
		} catch (NumberFormatException ignorada) {
			return null;
		}
	}

	/** Especie del sistema a partir del texto libre {@code Especie_Raza}. */
	public static Mascota.Especie especie(String valor) {
		if (valor == null) {
			return null;
		}
		String limpio = valor.toLowerCase(ES_AR);
		if (limpio.contains("gato") || limpio.contains("gata") || limpio.contains("felino")
				|| limpio.contains("siames")) {
			return Mascota.Especie.FELINO;
		}
		if (limpio.contains("perro") || limpio.contains("canino") || limpio.contains("labrador")
				|| limpio.contains("bulldog") || limpio.contains("ovejero") || limpio.contains("caniche")
				|| limpio.contains("doberman") || limpio.contains("mestizo")) {
			return Mascota.Especie.CANINO;
		}
		return Mascota.Especie.OTRO;
	}

	/** Raza legible a partir del texto libre. {@code null} si el origen no la distingue. */
	public static String raza(String valor, Mascota.Especie especie) {
		if (valor == null) {
			return null;
		}
		String limpio = valor.toLowerCase(ES_AR);
		if (limpio.contains("labrador")) {
			return "Labrador";
		}
		if (limpio.contains("bulldog")) {
			return "Bulldog Frances";
		}
		if (limpio.contains("siames")) {
			return "Siames";
		}
		if (especie == Mascota.Especie.FELINO && limpio.contains("comun")) {
			return "Comun";
		}
		if (limpio.contains("ovejero")) {
			return "Ovejero Aleman";
		}
		if (limpio.contains("caniche")) {
			if (limpio.contains("toy")) {
				return "Caniche Toy";
			}
			if (limpio.contains("blanco")) {
				return "Caniche Blanco";
			}
			return "Caniche";
		}
		if (limpio.contains("doberman")) {
			return "Doberman";
		}
		if (limpio.contains("mestizo")) {
			return "Mestizo";
		}
		return null;
	}

	/**
	 * Modalidad de guarderia al enum del sistema.
	 *
	 * <p>El origen no tiene un catalogo: {@code "nocturna"}, {@code "noches"} y
	 * {@code "felina"} se tratan como {@link ReservaGuarderia.TipoEstadia#NOCTURNA};
	 * {@code "semanal"} y {@code "prolongada"} como
	 * {@link ReservaGuarderia.TipoEstadia#COMPLETA_24H}; el resto como
	 * {@link ReservaGuarderia.TipoEstadia#DIURNA}.</p>
	 */
	public static ReservaGuarderia.TipoEstadia modalidad(String valor) {
		if (valor == null) {
			return ReservaGuarderia.TipoEstadia.DIURNA;
		}
		String limpio = valor.toLowerCase(ES_AR);
		if (limpio.contains("nocturna") || limpio.contains("noches") || limpio.contains("felina")) {
			return ReservaGuarderia.TipoEstadia.NOCTURNA;
		}
		if (limpio.contains("semanal") || limpio.contains("prolongada")) {
			return ReservaGuarderia.TipoEstadia.COMPLETA_24H;
		}
		return ReservaGuarderia.TipoEstadia.DIURNA;
	}

	/** Quita el titulo profesional ("Dr.", "Dra.") del nombre del veterinario. */
	public static String sinHonorifico(String valor) {
		if (valor == null) {
			return null;
		}
		return HONORIFICO.matcher(valor).replaceFirst("").trim();
	}

	/** Separa nombre y apellido: el ultimo token es el apellido. */
	public static String[] nombreYApellido(String valor) {
		String limpio = NormalizadorAr.nombrePropio(sinHonorifico(valor));
		if (limpio == null || limpio.isBlank()) {
			return new String[] { "", "" };
		}
		int ultimoEspacio = limpio.lastIndexOf(' ');
		if (ultimoEspacio < 0) {
			return new String[] { limpio, "" };
		}
		return new String[] {
				limpio.substring(0, ultimoEspacio).trim(),
				limpio.substring(ultimoEspacio + 1).trim() };
	}

	/** Clave de comparacion de nombres: sin titulo, capitalizado y en minuscula. */
	public static String claveNombre(String valor) {
		String limpio = NormalizadorAr.nombrePropio(sinHonorifico(valor));
		return limpio == null ? "" : limpio.toLowerCase(Locale.ROOT);
	}

	/** Nombre completo normalizado, para mostrar en el reporte. */
	public static String nombreCompleto(String valor) {
		return NormalizadorAr.nombrePropio(sinHonorifico(valor));
	}

	/** Numero de ficha o reserva: {@code "AT-501"} -> 501. */
	public static Integer numeroRegistro(String valor) {
		if (valor == null) {
			return null;
		}
		String digitos = valor.replaceAll("[^0-9]", "");
		if (digitos.isEmpty()) {
			return null;
		}
		try {
			return Integer.valueOf(digitos);
		} catch (NumberFormatException ignorada) {
			return null;
		}
	}
}
