package com.veterinaria.veterinaria_backend.importacion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Lee un libro {@code .xlsx} a filas de texto.
 *
 * <p>{@link DataFormatter} aplica el formato visible de la celda: un numero
 * guardado como {@code 12000} llega como {@code "12000"} y una fecha con formato
 * de celda llega como la ve el usuario, no como el serial de Excel. No se
 * evaluan formulas porque la planilla no las usa.</p>
 */
@Component
public class LectorExcel {

	private static final Locale ES_AR = Locale.forLanguageTag("es-AR");

	public LibroExcel leer(Path archivo) throws IOException {
		try (InputStream entrada = Files.newInputStream(archivo);
				Workbook libro = new XSSFWorkbook(entrada)) {
			DataFormatter formatter = new DataFormatter(ES_AR);
			Map<String, HojaExcel> hojas = new LinkedHashMap<>();

			for (int indice = 0; indice < libro.getNumberOfSheets(); indice++) {
				Sheet hoja = libro.getSheetAt(indice);
				List<List<String>> filas = new ArrayList<>();

				for (Row fila : hoja) {
					int ultimaCelda = Math.max(fila.getLastCellNum(), 0);
					List<String> celdas = new ArrayList<>(ultimaCelda);
					for (int columna = 0; columna < ultimaCelda; columna++) {
						Cell celda = fila.getCell(columna, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
						celdas.add(celda == null ? "" : formatter.formatCellValue(celda).trim());
					}
					filas.add(celdas);
				}

				hojas.put(hoja.getSheetName(), new HojaExcel(hoja.getSheetName(), filas));
			}

			return new LibroExcel(hojas);
		}
	}

	public record HojaExcel(String nombre, List<List<String>> filas) {

		/** Fila por indice 1-based de Excel; vacia si no existe. */
		public List<String> fila(int numeroFila) {
			int indice = numeroFila - 1;
			return indice >= 0 && indice < filas.size() ? filas.get(indice) : List.of();
		}
	}

	public record LibroExcel(Map<String, HojaExcel> hojas) {

		public HojaExcel hoja(String nombre) {
			return hojas.get(nombre);
		}
	}
}
