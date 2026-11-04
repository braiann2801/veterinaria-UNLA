package com.veterinaria.veterinaria_backend.importacion;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Punto de entrada de la importacion unica.
 *
 * <p>Deshabilitado por defecto. Es un {@link CommandLineRunner} y no un
 * endpoint a proposito: se corre una sola vez desde la consola, no queda
 * expuesto en la API y no se puede disparar desde el frontend.</p>
 */
@Component
@RequiredArgsConstructor
public class ImportacionRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(ImportacionRunner.class);

	private final ImportacionProperties properties;
	private final LectorExcel lector;
	private final ConstructorPlan constructor;
	private final ImportacionService service;

	@Override
	public void run(String... args) {
		if (!properties.isHabilitada()) {
			return;
		}

		Path archivo = Path.of(properties.getArchivo()).toAbsolutePath().normalize();
		if (!Files.isRegularFile(archivo)) {
			log.error("Importacion habilitada pero el archivo no existe: {}", archivo);
			return;
		}

		try {
			LectorExcel.LibroExcel libro = lector.leer(archivo);
			PlanImportacion plan = constructor.construir(libro);

			boolean aplicar = properties.isAplicar() && plan.erroresFatales().isEmpty();
			if (properties.isAplicar() && !plan.erroresFatales().isEmpty()) {
				log.warn("Se pidio aplicar pero el plan tiene errores fatales: se genera solo el reporte.");
			}

			ResultadoImportacion resultado = aplicar
					? service.aplicar(plan)
					: service.simular(plan);

			Path reporte = new ReporteImportacion().escribir(
					archivo.getParent(), archivo, plan, resultado, aplicar);

			log.info("Importacion {}: tutores={} ({} existentes), mascotas={} ({} existentes), "
							+ "consultas={} ({} omitidas), guarderias={} ({} omitidas), comandas={}, pagos={}",
					aplicar ? "APLICADA" : "SIMULADA",
					resultado.tutoresCreados(), resultado.tutoresExistentes(),
					resultado.mascotasCreadas(), resultado.mascotasExistentes(),
					resultado.consultasCreadas(), resultado.consultasOmitidas(),
					resultado.guarderiasCreadas(), resultado.guarderiasOmitidas(),
					resultado.comandasCreadas(), resultado.pagosCreados());
			log.info("Reporte de importacion: {}", reporte);
		} catch (Exception excepcion) {
			log.error("Fallo la importacion; no se aplicaron cambios: {}", excepcion.getMessage(), excepcion);
		}
	}
}
