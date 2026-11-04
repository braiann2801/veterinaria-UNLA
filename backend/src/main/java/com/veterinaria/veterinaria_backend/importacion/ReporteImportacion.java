package com.veterinaria.veterinaria_backend.importacion;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Genera el reporte Markdown de la importacion.
 *
 * <p>Se escribe tanto en simulacion como en aplicado. El reporte de simulacion
 * es el que se revisa antes de tocar la base; el de aplicado deja constancia de
 * lo que quedo escrito. Incluye tablas con todos los registros planificados,
 * los conflictos fila por fila y los campos del Excel que no tienen destino en
 * el esquema.</p>
 */
public final class ReporteImportacion {

	private static final DateTimeFormatter NOMBRE_ARCHIVO =
			DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final DateTimeFormatter FECHA_HORA =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public Path escribir(
			Path directorio,
			Path archivo,
			PlanImportacion plan,
			ResultadoImportacion resultado,
			boolean aplicado) throws IOException {

		Path destinoDirectorio = directorio == null ? Path.of(".") : directorio;
		Files.createDirectories(destinoDirectorio);

		String marca = aplicado ? "aplicado" : "simulacion";
		String nombre = "reporte-importacion-"
				+ LocalDateTime.now().format(NOMBRE_ARCHIVO)
				+ "-" + marca + ".md";
		Path destino = destinoDirectorio.resolve(nombre);

		StringBuilder md = new StringBuilder();
		md.append("# Reporte de importacion - Veterinaria Huellitas\n\n");
		md.append("- Archivo fuente: `").append(archivo.toAbsolutePath().normalize()).append("`\n");
		md.append("- Fecha del reporte: ").append(LocalDateTime.now().format(FECHA_HORA)).append("\n");
		md.append("- Modo: **").append(aplicado ? "APLICADO" : "SIMULACION").append("**\n");
		md.append("- Efecto: ").append(aplicado
				? "los datos se escribieron en la base en una sola transaccion."
				: "no se escribio nada en la base; este reporte es solo una previsualizacion.")
				.append("\n\n");

		if (!plan.erroresFatales().isEmpty()) {
			md.append("## Errores fatales\n\n");
			for (String error : plan.erroresFatales()) {
				md.append("- ").append(error).append("\n");
			}
			md.append("\n");
		}

		if (resultado != null) {
			md.append("## Resumen de la corrida\n\n");
			md.append("| Concepto | Creados | Existentes / omitidos |\n");
			md.append("|---|---:|---:|\n");
			md.append(filaResumen("Tutores", resultado.tutoresCreados(), resultado.tutoresExistentes()));
			md.append(filaResumen("Mascotas", resultado.mascotasCreadas(), resultado.mascotasExistentes()));
			md.append("| Vinculos tutor-mascota | ").append(resultado.vinculosCreados()).append(" | - |\n");
			md.append(filaResumen("Profesionales", resultado.profesionalesCreados(),
					resultado.profesionalesExistentes()));
			md.append(filaResumen("Consultas", resultado.consultasCreadas(), resultado.consultasOmitidas()));
			md.append(filaResumen("Guarderias", resultado.guarderiasCreadas(),
					resultado.guarderiasOmitidas()));
			md.append("| Comandas | ").append(resultado.comandasCreadas()).append(" | - |\n");
			md.append("| Pagos | ").append(resultado.pagosCreados()).append(" | - |\n\n");
		}

		md.append("## 1. Padron: tutores y mascotas\n\n");
		md.append("### Tutores\n\n");
		md.append("| DNI provisional | Nombre | Telefono | Direccion | Filas Excel |\n");
		md.append("|---|---|---|---|---|\n");
		for (PlanImportacion.TutorPlan tutor : plan.tutores()) {
			md.append("| ").append(md(tutor.dni()))
					.append(" | ").append(md(tutor.nombre() + " " + tutor.apellido()))
					.append(" | ").append(md(tutor.telefono()))
					.append(" | ").append(md(tutor.direccion()))
					.append(" | ").append(filas(tutor.filas()))
					.append(" |\n");
		}
		md.append("\n### Mascotas\n\n");
		md.append("| Mascota | Tutor | Especie | Raza | Nac. aproximada | Peso | Edad origen | Filas Excel |\n");
		md.append("|---|---|---|---|---|---:|---|---|\n");
		for (PlanImportacion.MascotaPlan mascota : plan.mascotas()) {
			md.append("| ").append(md(mascota.nombre()))
					.append(" | ").append(md(mascota.tutorNombre()))
					.append(" | ").append(mascota.especie())
					.append(" | ").append(md(mascota.raza()))
					.append(" | ").append(fecha(mascota.fechaNacimiento()))
					.append(" | ").append(peso(mascota.pesoKg()))
					.append(" | ").append(md(mascota.edadTexto()))
					.append(" | ").append(filas(mascota.filas()))
					.append(" |\n");
		}

		md.append("\n## 2. Atenciones clinicas\n\n");
		md.append("| Ficha | Fecha | Mascota | Tutor | Profesional | Total | Pagos | Saldo | Nota de conciliacion |\n");
		md.append("|---|---|---|---|---|---:|---|---:|---|\n");
		for (PlanImportacion.ConsultaPlan consulta : plan.consultas()) {
			md.append("| ").append(md(consulta.nroFicha()))
					.append(" | ").append(consulta.fecha())
					.append(" | ").append(md(consulta.mascotaNombre()))
					.append(" | ").append(md(consulta.tutorNombre()))
					.append(" | ").append(md(consulta.matriculaProfesional()))
					.append(" | ").append(money(consulta.total()))
					.append(" | ").append(pagos(consulta.pagos()))
					.append(" | ").append(money(consulta.saldo()))
					.append(" | ").append(md(consulta.conciliacionNota()))
					.append(" |\n");
		}

		md.append("\n## 3. Guarderia y estadias\n\n");
		md.append("| Reserva | Ingreso | Egreso | Mascota | Tutor | Modalidad | Total | Sena | Pagos | Saldo | Nota de conciliacion |\n");
		md.append("|---|---|---|---|---|---|---:|---:|---|---:|---|\n");
		for (PlanImportacion.GuarderiaPlan guarderia : plan.guarderias()) {
			md.append("| ").append(md(guarderia.nroReserva()))
					.append(" | ").append(guarderia.ingreso())
					.append(" | ").append(guarderia.egreso())
					.append(" | ").append(md(guarderia.mascotaNombre()))
					.append(" | ").append(md(guarderia.tutorNombre()))
					.append(" | ").append(guarderia.tipoEstadia())
					.append(" | ").append(money(guarderia.total()))
					.append(" | ").append(money(guarderia.sena()))
					.append(" | ").append(pagos(guarderia.pagos()))
					.append(" | ").append(money(guarderia.saldo()))
					.append(" | ").append(md(guarderia.conciliacionNota()))
					.append(" |\n");
		}

		md.append("\n## 4. Incidencias detectadas\n\n");
		if (plan.incidencias().isEmpty()) {
			md.append("No se detectaron conflictos ni errores de fila.\n\n");
		} else {
			for (PlanImportacion.TipoIncidencia tipo : PlanImportacion.TipoIncidencia.values()) {
				List<PlanImportacion.Incidencia> delTipo = plan.incidencias().stream()
						.filter(incidencia -> incidencia.tipo() == tipo)
						.toList();
				if (delTipo.isEmpty()) {
					continue;
				}
				md.append("### ").append(tipo).append("\n\n");
				for (PlanImportacion.Incidencia incidencia : delTipo) {
					md.append("- [").append(md(incidencia.hoja()))
							.append(" fila ").append(incidencia.fila()).append("] ")
							.append(incidencia.mensaje()).append("\n");
				}
				md.append("\n");
			}
		}

		md.append("## 5. Transformaciones y supuestos\n\n");
		for (String transformacion : plan.transformaciones()) {
			md.append("- ").append(transformacion).append("\n");
		}

		md.append("\n## 6. Campos del Excel sin destino en el esquema\n\n");
		for (String campo : plan.camposSinDestino()) {
			md.append("- ").append(campo).append("\n");
		}

		md.append("\n## 7. Profesionales placeholder\n\n");
		md.append("| Nombre | Matricula | Origen en Excel |\n");
		md.append("|---|---|---|\n");
		for (PlanImportacion.ProfesionalPlan profesional : plan.profesionales()) {
			md.append("| ").append(md(profesional.nombre() + " " + profesional.apellido()))
					.append(" | ").append(md(profesional.matricula()))
					.append(" | ").append(md(profesional.origen()))
					.append(" |\n");
		}

		md.append("\n---\n\n");
		md.append("Este reporte contiene datos personales. No versionar: `datos/` esta en `.gitignore`.\n");

		Files.writeString(destino, md.toString(), StandardCharsets.UTF_8);
		return destino;
	}

	private static String filaResumen(String concepto, int creados, int existentes) {
		return "| " + concepto + " | " + creados + " | " + existentes + " |\n";
	}

	private static String filas(List<Integer> filas) {
		return filas.stream().map(String::valueOf).collect(Collectors.joining(", "));
	}

	private static String pagos(List<PlanImportacion.PagoPlan> pagos) {
		if (pagos.isEmpty()) {
			return "Sin pago";
		}
		return pagos.stream()
				.map(pago -> pago.metodo() + " " + money(pago.monto()))
				.collect(Collectors.joining("<br>"));
	}

	private static String money(BigDecimal valor) {
		if (valor == null) {
			return "-";
		}
		return "$ " + valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	private static String peso(BigDecimal valor) {
		if (valor == null) {
			return "-";
		}
		return valor.stripTrailingZeros().toPlainString() + " kg";
	}

	private static String fecha(LocalDate valor) {
		return valor == null ? "-" : valor.toString();
	}

	private static String md(String valor) {
		if (valor == null || valor.isBlank()) {
			return "-";
		}
		return valor.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
	}
}
