package com.veterinaria.veterinaria_backend.importacion;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.veterinaria.veterinaria_backend.entity.ConsultaMedica;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.Pago;
import com.veterinaria.veterinaria_backend.entity.ReservaGuarderia;

/**
 * Convierte las tres hojas del Excel en un {@link PlanImportacion}.
 *
 * <p>No escribe nada: solo lee, interpreta, resuelve tutores/mascotas entre
 * hojas y acumula incidencias. Toda decision que no surja directo de la
 * planilla (DNI provisional, edad a fecha de nacimiento, medio de pago
 * asumido) queda anotada en {@code transformaciones} y termina en el reporte.</p>
 */
@Component
public class ConstructorPlan {

	private static final String HOJA_PADRON = "Clientes_y_Mascotas";
	private static final String HOJA_ATENCIONES = "Atenciones_Clinicas";
	private static final String HOJA_GUARDERIA = "Guarderia_y_Estadias";

	private static final String APELLIDO_PLACEHOLDER = "Historico";

	public PlanImportacion construir(LectorExcel.LibroExcel libro) {
		List<String> erroresFatales = new ArrayList<>();
		List<PlanImportacion.Incidencia> incidencias = new ArrayList<>();
		List<String> transformaciones = new ArrayList<>();
		List<String> camposSinDestino = camposSinDestino();

		LocalDate hoy = LocalDate.now();
		transformaciones.add("Edad_Aprox no existe como campo: la fecha de nacimiento se estimo "
				+ "restandole la edad aproximada a la fecha de importacion (" + hoy + ").");
		transformaciones.add("La hoja 1 no trae DNI: se asignaron DNIs provisionales "
				+ "00000001..00000009 en orden de primera aparicion. Son unicos y no se "
				+ "corresponden con el documento real de los tutores.");
		transformaciones.add("Telefonos: se normalizaron a 10 digitos. Cuando faltaba el codigo "
				+ "de area se infirio el 11 (CABA). Si no habia ningun digito se uso un numero "
				+ "provisorio 000000000N.");
		transformaciones.add("Especie_Raza se descompuso en especie (CANINO/FELINO) y raza. "
				+ "El texto original no se conserva en la entidad.");
		transformaciones.add("La planilla no trae horas: se usan 10:00 para la atencion, "
				+ "10:30 para los pagos de consulta y 09:00/10:00 para los movimientos de "
				+ "guarderia. Sirven solo para ordenar el historial.");
		transformaciones.add("Los importes de la hoja 2 estan embebidos en texto libre. Se "
				+ "conciliaron fila por fila contra el detalle (ver columna Nota) y se "
				+ "registraron como pagos historicos, no con la tarifa vigente.");
		transformaciones.add("La hoja 3 trae Tarifa_Total y Estado_Pago por separado. El estado "
				+ "se convirtio en pagos (EFECTIVO/TRANSFERENCIA/SENA) mas saldo pendiente; "
				+ "las estadias quedan FINALIZADO.");

		LectorExcel.HojaExcel hojaPadron = libro.hoja(HOJA_PADRON);
		LectorExcel.HojaExcel hojaAtenciones = libro.hoja(HOJA_ATENCIONES);
		LectorExcel.HojaExcel hojaGuarderia = libro.hoja(HOJA_GUARDERIA);

		if (hojaPadron == null) {
			erroresFatales.add("Falta la hoja " + HOJA_PADRON + " en el libro.");
		}
		if (hojaAtenciones == null) {
			erroresFatales.add("Falta la hoja " + HOJA_ATENCIONES + " en el libro.");
		}
		if (hojaGuarderia == null) {
			erroresFatales.add("Falta la hoja " + HOJA_GUARDERIA + " en el libro.");
		}

		Map<String, TutorBuilder> tutoresPorClave = new LinkedHashMap<>();
		Map<String, MascotaBuilder> mascotasPorClave = new LinkedHashMap<>();
		Map<String, ProfesionalBuilder> profesionalesPorClave = new LinkedHashMap<>();

		if (hojaPadron != null) {
			construirPadron(hojaPadron, hoy, tutoresPorClave, mascotasPorClave,
					incidencias, transformaciones, erroresFatales);
		}

		List<PlanImportacion.TutorPlan> tutores = tutoresPorClave.values().stream()
				.map(builder -> new PlanImportacion.TutorPlan(
						builder.dni,
						builder.nombre,
						builder.apellido,
						builder.telefono,
						builder.email,
						builder.direccion,
						List.copyOf(builder.filas)))
				.toList();

		List<PlanImportacion.MascotaPlan> mascotas = mascotasPorClave.values().stream()
				.map(builder -> new PlanImportacion.MascotaPlan(
						builder.tutorDni,
						builder.tutorNombre,
						builder.nombre,
						builder.especie,
						builder.raza,
						builder.fechaNacimiento,
						builder.pesoKg,
						builder.edadMeses,
						builder.edadTexto,
						builder.especieRazaTexto,
						List.copyOf(builder.filas)))
				.toList();

		List<PlanImportacion.ConsultaPlan> consultas = new ArrayList<>();
		if (hojaAtenciones != null) {
			construirConsultas(hojaAtenciones, mascotasPorClave, profesionalesPorClave,
					consultas, incidencias, transformaciones, erroresFatales);
		}

		List<PlanImportacion.GuarderiaPlan> guarderias = new ArrayList<>();
		if (hojaGuarderia != null) {
			construirGuarderias(hojaGuarderia, mascotasPorClave, guarderias,
					incidencias, transformaciones, erroresFatales);
		}

		List<PlanImportacion.ProfesionalPlan> profesionales = profesionalesPorClave.values().stream()
				.map(builder -> new PlanImportacion.ProfesionalPlan(
						builder.nombre,
						builder.apellido,
						builder.matricula,
						builder.origen))
				.toList();

		return new PlanImportacion(
				tutores,
				mascotas,
				profesionales,
				consultas,
				guarderias,
				incidencias,
				List.copyOf(transformaciones),
				List.copyOf(camposSinDestino),
				List.copyOf(erroresFatales));
	}

	// ------------------------------------------------------------------ padron

	private void construirPadron(
			LectorExcel.HojaExcel hoja,
			LocalDate hoy,
			Map<String, TutorBuilder> tutoresPorClave,
			Map<String, MascotaBuilder> mascotasPorClave,
			List<PlanImportacion.Incidencia> incidencias,
			List<String> transformaciones,
			List<String> erroresFatales) {

		Map<String, Integer> columnas = columnas(hoja);
		if (!tieneColumnas(columnas, "Nombre_Duenio", "Telefono_Contacto", "Nombre_Mascota",
				"Especie_Raza", "Edad_Aprox", "Peso_Kg", "Direccion_Localidad")) {
			erroresFatales.add("La hoja " + hoja.nombre() + " no tiene las columnas esperadas.");
			return;
		}

		int telefonosProvisionales = 0;

		for (int numeroFila = 2; numeroFila <= hoja.filas().size(); numeroFila++) {
			List<String> fila = hoja.fila(numeroFila);
			if (filaVacia(fila)) {
				continue;
			}

			String tutorTexto = celda(fila, columnas, "Nombre_Duenio");
			String telefonoTexto = celda(fila, columnas, "Telefono_Contacto");
			String mascotaTexto = celda(fila, columnas, "Nombre_Mascota");
			String especieRazaTexto = celda(fila, columnas, "Especie_Raza");
			String edadTexto = celda(fila, columnas, "Edad_Aprox");
			String pesoTexto = celda(fila, columnas, "Peso_Kg");
			String direccion = celda(fila, columnas, "Direccion_Localidad");
			String idRegistro = celda(fila, columnas, "ID_Registro");

			if (tutorTexto.isBlank() || mascotaTexto.isBlank()) {
				incidencias.add(new PlanImportacion.Incidencia(
						PlanImportacion.TipoIncidencia.ERROR, hoja.nombre(), numeroFila,
						"Fila sin nombre de tutor o de mascota (ID_Registro=" + idRegistro + ")."));
				continue;
			}

			String tutorClave = ParseoDatos.claveNombre(tutorTexto);
			String mascotaClave = ParseoDatos.claveNombre(mascotaTexto);
			if (tutorClave.isBlank() || mascotaClave.isBlank()) {
				incidencias.add(new PlanImportacion.Incidencia(
						PlanImportacion.TipoIncidencia.ERROR, hoja.nombre(), numeroFila,
						"No se pudo normalizar el nombre del tutor o de la mascota."));
				continue;
			}

			TutorBuilder tutor = tutoresPorClave.get(tutorClave);
			if (tutor == null) {
				String[] nombreApellido = ParseoDatos.nombreYApellido(tutorTexto);
				String telefono = ParseoDatos.telefono(telefonoTexto, "");
				boolean provisorio = telefono.isBlank();
				if (provisorio) {
					telefonosProvisionales++;
					telefono = String.format("%010d", telefonosProvisionales);
					transformaciones.add("Telefono de " + ParseoDatos.nombreCompleto(tutorTexto)
							+ ": la hoja no trae un numero usable; se asigno " + telefono + ".");
				} else if (telefonoTexto.contains("0341") || telefonoTexto.contains("15-99")
						|| telefonoTexto.startsWith("15")) {
					transformaciones.add("Telefono de " + ParseoDatos.nombreCompleto(tutorTexto)
							+ ": '" + telefonoTexto + "' se normalizo a " + telefono
							+ " (se infirio el codigo de area).");
				}

				tutor = new TutorBuilder();
				tutor.dni = String.format("%08d", tutoresPorClave.size() + 1);
				tutor.nombre = nombreApellido[0];
				tutor.apellido = nombreApellido[1];
				tutor.telefono = telefono;
				tutor.telefonoProvisorio = provisorio;
				tutor.direccion = direccion.isBlank() ? null : direccion;
				tutor.filas.add(numeroFila);
				tutoresPorClave.put(tutorClave, tutor);
			} else {
				tutor.filas.add(numeroFila);

				String telefono = ParseoDatos.telefono(telefonoTexto, "");
				if (!telefono.isBlank() && !telefono.equals(tutor.telefono)) {
					if (tutor.telefonoProvisorio) {
						tutor.telefono = telefono;
						tutor.telefonoProvisorio = false;
						transformaciones.add("Telefono de " + tutor.nombre + " " + tutor.apellido
								+ ": se completo con el de la fila " + numeroFila + " (" + telefono + ").");
					} else {
						incidencias.add(new PlanImportacion.Incidencia(
								PlanImportacion.TipoIncidencia.CONFLICTO, hoja.nombre(), numeroFila,
								"Telefono distinto para " + tutor.nombre + " " + tutor.apellido
										+ ": se conserva " + tutor.telefono + " de la primera fila; "
										+ "la fila dice " + telefono + "."));
					}
				}

				if (!direccion.isBlank()) {
					if (tutor.direccion == null || tutor.direccion.isBlank()) {
						tutor.direccion = direccion;
					} else if (!tutor.direccion.equals(direccion)) {
						incidencias.add(new PlanImportacion.Incidencia(
								PlanImportacion.TipoIncidencia.CONFLICTO, hoja.nombre(), numeroFila,
								"Direccion distinta para " + tutor.nombre + " " + tutor.apellido
										+ ": se conserva '" + tutor.direccion + "'; la fila dice '"
										+ direccion + "'."));
					}
				}
			}

			Mascota.Especie especie = ParseoDatos.especie(especieRazaTexto);
			String raza = ParseoDatos.raza(especieRazaTexto, especie);
			Integer edadMeses = ParseoDatos.mesesDesdeEdad(edadTexto);
			LocalDate fechaNacimiento = edadMeses == null ? null : hoy.minusMonths(edadMeses);
			BigDecimal peso = ParseoDatos.peso(pesoTexto);

			if (especieRazaTexto.isBlank()) {
				especie = Mascota.Especie.OTRO;
				incidencias.add(new PlanImportacion.Incidencia(
						PlanImportacion.TipoIncidencia.AVISO, hoja.nombre(), numeroFila,
						"Especie_Raza vacia; se carga como OTRO."));
			} else if (especie == null || especie == Mascota.Especie.OTRO) {
				especie = Mascota.Especie.OTRO;
				incidencias.add(new PlanImportacion.Incidencia(
						PlanImportacion.TipoIncidencia.AVISO, hoja.nombre(), numeroFila,
						"Especie_Raza '" + especieRazaTexto + "' no se reconoce; se carga como OTRO."));
			}
			if (edadMeses == null) {
				incidencias.add(new PlanImportacion.Incidencia(
						PlanImportacion.TipoIncidencia.AVISO, hoja.nombre(), numeroFila,
						"Edad_Aprox '" + edadTexto + "' no se entiende; fechaNacimiento queda null."));
			}
			if (peso == null) {
				incidencias.add(new PlanImportacion.Incidencia(
						PlanImportacion.TipoIncidencia.AVISO, hoja.nombre(), numeroFila,
						"Peso_Kg '" + pesoTexto + "' no se entiende; pesoKg queda null."));
			}
			if (raza == null) {
				incidencias.add(new PlanImportacion.Incidencia(
						PlanImportacion.TipoIncidencia.AVISO, hoja.nombre(), numeroFila,
						"Especie_Raza '" + especieRazaTexto + "' no distingue una raza; raza queda null."));
			}

			String claveMascota = mascotaClave(tutorClave, mascotaClave);
			MascotaBuilder mascota = mascotasPorClave.get(claveMascota);
			if (mascota == null) {
				mascota = new MascotaBuilder();
				mascota.tutorDni = tutor.dni;
				mascota.tutorNombre = tutor.nombre + " " + tutor.apellido;
				mascota.nombre = ParseoDatos.nombreCompleto(mascotaTexto);
				mascota.especie = especie;
				mascota.raza = raza;
				mascota.fechaNacimiento = fechaNacimiento;
				mascota.pesoKg = peso;
				mascota.edadMeses = edadMeses;
				mascota.edadTexto = edadTexto;
				mascota.especieRazaTexto = especieRazaTexto;
				mascota.filas.add(numeroFila);
				mascotasPorClave.put(claveMascota, mascota);
			} else {
				mascota.filas.add(numeroFila);

				if (peso != null && mascota.pesoKg != null && peso.compareTo(mascota.pesoKg) != 0) {
					incidencias.add(new PlanImportacion.Incidencia(
							PlanImportacion.TipoIncidencia.CONFLICTO, hoja.nombre(), numeroFila,
							"Peso distinto para " + mascota.nombre + ": se conserva "
									+ mascota.pesoKg.toPlainString() + " kg de la primera fila; la fila dice "
									+ peso.toPlainString() + " kg."));
				}
				if (especie != null && mascota.especie != null && especie != mascota.especie) {
					incidencias.add(new PlanImportacion.Incidencia(
							PlanImportacion.TipoIncidencia.CONFLICTO, hoja.nombre(), numeroFila,
							"Especie distinta para " + mascota.nombre + ": se conserva "
									+ mascota.especie + "; la fila dice " + especie + "."));
				}
				if (raza != null && mascota.raza != null && !raza.equals(mascota.raza)) {
					incidencias.add(new PlanImportacion.Incidencia(
							PlanImportacion.TipoIncidencia.CONFLICTO, hoja.nombre(), numeroFila,
							"Raza distinta para " + mascota.nombre + ": se conserva '"
									+ mascota.raza + "'; la fila dice '" + raza + "'."));
				}
				if (edadMeses != null && mascota.edadMeses != null
						&& !edadMeses.equals(mascota.edadMeses)) {
					incidencias.add(new PlanImportacion.Incidencia(
							PlanImportacion.TipoIncidencia.CONFLICTO, hoja.nombre(), numeroFila,
							"Edad distinta para " + mascota.nombre + ": se conserva "
									+ mascota.edadTexto + "; la fila dice " + edadTexto + "."));
				}
			}
		}
	}

	// ------------------------------------------------------------- consultas

	private void construirConsultas(
			LectorExcel.HojaExcel hoja,
			Map<String, MascotaBuilder> mascotasPorClave,
			Map<String, ProfesionalBuilder> profesionalesPorClave,
			List<PlanImportacion.ConsultaPlan> consultas,
			List<PlanImportacion.Incidencia> incidencias,
			List<String> transformaciones,
			List<String> erroresFatales) {

		Map<String, Integer> columnas = columnas(hoja);
		if (!tieneColumnas(columnas, "Nro_Ficha", "Fecha_Visita", "Paciente_Mascota",
				"Duenio_Referencia", "Tipo_Servicio", "Detalle_Clinico_y_Cobro",
				"Veterinario_Tratante")) {
			erroresFatales.add("La hoja " + hoja.nombre() + " no tiene las columnas esperadas.");
			return;
		}

		for (int numeroFila = 2; numeroFila <= hoja.filas().size(); numeroFila++) {
			List<String> fila = hoja.fila(numeroFila);
			if (filaVacia(fila)) {
				continue;
			}

			String nroFicha = celda(fila, columnas, "Nro_Ficha");
			String fechaTexto = celda(fila, columnas, "Fecha_Visita");
			String mascotaTexto = celda(fila, columnas, "Paciente_Mascota");
			String tutorTexto = celda(fila, columnas, "Duenio_Referencia");
			String tipoServicio = celda(fila, columnas, "Tipo_Servicio");
			String detalle = celda(fila, columnas, "Detalle_Clinico_y_Cobro");
			String veterinario = celda(fila, columnas, "Veterinario_Tratante");

			Integer numero = ParseoDatos.numeroRegistro(nroFicha);
			if (numero == null) {
				incidencias.add(error(hoja, numeroFila,
						"Nro_Ficha '" + nroFicha + "' no tiene un numero interpretable."));
				continue;
			}

			ConciliacionHistorica.Conciliacion conciliacion = ConciliacionHistorica.consulta(numero);
			if (conciliacion == null) {
				incidencias.add(error(hoja, numeroFila,
						"No hay conciliacion de cobro para la ficha " + nroFicha + "."));
				continue;
			}

			LocalDate fecha = ParseoDatos.fecha(fechaTexto);
			if (fecha == null) {
				incidencias.add(error(hoja, numeroFila,
						"Fecha_Visita '" + fechaTexto + "' no se entiende."));
				continue;
			}

			MascotaBuilder mascota = buscarMascota(mascotasPorClave, tutorTexto, mascotaTexto);
			if (mascota == null) {
				incidencias.add(error(hoja, numeroFila,
						"La mascota '" + mascotaTexto + "' de '" + tutorTexto
								+ "' no aparece en " + HOJA_PADRON + "."));
				continue;
			}

			String[] nombreApellido = ParseoDatos.nombreYApellido(veterinario);
			if (nombreApellido[0].isBlank()) {
				incidencias.add(error(hoja, numeroFila,
						"Veterinario_Tratante '" + veterinario + "' no tiene nombre."));
				continue;
			}
			String claveProfesional = ParseoDatos.claveNombre(veterinario);
			ProfesionalBuilder profesional = profesionalesPorClave.get(claveProfesional);
			if (profesional == null) {
				profesional = new ProfesionalBuilder();
				profesional.nombre = nombreApellido[0];
				profesional.apellido = APELLIDO_PLACEHOLDER;
				profesional.matricula = matriculaPlaceholder(nombreApellido);
				profesional.origen = veterinario;
				profesionalesPorClave.put(claveProfesional, profesional);
				transformaciones.add("Profesional '" + veterinario + "': no existe en la base. "
						+ "Se crea como " + profesional.nombre + " " + profesional.apellido
						+ " con matricula provisional " + profesional.matricula + ".");
			}

			List<PlanImportacion.PagoPlan> pagos = pagosDe(conciliacion, fecha, LocalTime.of(10, 30));
			ConsultaMedica.ConductaObservada conducta = detalle.toLowerCase(Locale.ROOT)
					.contains("agresivo")
							? ConsultaMedica.ConductaObservada.AGRESIVO
							: ConsultaMedica.ConductaObservada.TRANQUILO;
			if (conducta == ConsultaMedica.ConductaObservada.AGRESIVO) {
				transformaciones.add("Ficha " + nroFicha + ": el detalle dice que el perro estuvo "
						+ "agresivo; se marca conductaObservada=AGRESIVO y alertaConducta=true.");
			}

			consultas.add(new PlanImportacion.ConsultaPlan(
					numero,
					nroFicha,
					fecha,
					mascota.tutorDni,
					mascota.tutorNombre,
					mascota.nombre,
					tipoServicio,
					detalle,
					profesional.matricula,
					conducta,
					conciliacion.total(),
					pagos,
					conciliacion.nota()));
		}
	}

	// ------------------------------------------------------------ guarderias

	private void construirGuarderias(
			LectorExcel.HojaExcel hoja,
			Map<String, MascotaBuilder> mascotasPorClave,
			List<PlanImportacion.GuarderiaPlan> guarderias,
			List<PlanImportacion.Incidencia> incidencias,
			List<String> transformaciones,
			List<String> erroresFatales) {

		Map<String, Integer> columnas = columnas(hoja);
		if (!tieneColumnas(columnas, "Nro_Reserva", "Mascota", "Tutor", "Fecha_Ingreso",
				"Fecha_Egreso", "Modalidad", "Tarifa_Total", "Estado_Pago",
				"Comportamiento_Cuidados")) {
			erroresFatales.add("La hoja " + hoja.nombre() + " no tiene las columnas esperadas.");
			return;
		}

		for (int numeroFila = 2; numeroFila <= hoja.filas().size(); numeroFila++) {
			List<String> fila = hoja.fila(numeroFila);
			if (filaVacia(fila)) {
				continue;
			}

			String nroReserva = celda(fila, columnas, "Nro_Reserva");
			String mascotaTexto = celda(fila, columnas, "Mascota");
			String tutorTexto = celda(fila, columnas, "Tutor");
			String ingresoTexto = celda(fila, columnas, "Fecha_Ingreso");
			String egresoTexto = celda(fila, columnas, "Fecha_Egreso");
			String modalidadTexto = celda(fila, columnas, "Modalidad");
			String tarifaTexto = celda(fila, columnas, "Tarifa_Total");
			String comportamiento = celda(fila, columnas, "Comportamiento_Cuidados");

			Integer numero = ParseoDatos.numeroRegistro(nroReserva);
			if (numero == null) {
				incidencias.add(error(hoja, numeroFila,
						"Nro_Reserva '" + nroReserva + "' no tiene un numero interpretable."));
				continue;
			}

			ConciliacionHistorica.Conciliacion conciliacion = ConciliacionHistorica.guarderia(numero);
			if (conciliacion == null) {
				incidencias.add(error(hoja, numeroFila,
						"No hay conciliacion de cobro para la reserva " + nroReserva + "."));
				continue;
			}

			LocalDate ingreso = ParseoDatos.fecha(ingresoTexto);
			LocalDate egreso = ParseoDatos.fecha(egresoTexto);
			if (ingreso == null || egreso == null) {
				incidencias.add(error(hoja, numeroFila,
						"Fechas de ingreso/egreso no interpretables: '" + ingresoTexto
								+ "' / '" + egresoTexto + "'."));
				continue;
			}
			if (egreso.isBefore(ingreso)) {
				incidencias.add(error(hoja, numeroFila,
						"Fecha_Egreso (" + egreso + ") es anterior a Fecha_Ingreso (" + ingreso + ")."));
				continue;
			}

			MascotaBuilder mascota = buscarMascota(mascotasPorClave, tutorTexto, mascotaTexto);
			if (mascota == null) {
				incidencias.add(error(hoja, numeroFila,
						"La mascota '" + mascotaTexto + "' de '" + tutorTexto
								+ "' no aparece en " + HOJA_PADRON + "."));
				continue;
			}

			BigDecimal totalHoja = ParseoDatos.dinero(tarifaTexto);
			if (totalHoja != null && totalHoja.compareTo(conciliacion.total()) != 0) {
				incidencias.add(new PlanImportacion.Incidencia(
						PlanImportacion.TipoIncidencia.CONFLICTO, hoja.nombre(), numeroFila,
						"Tarifa_Total de la hoja (" + totalHoja.toPlainString()
								+ ") no coincide con la conciliacion ("
								+ conciliacion.total().toPlainString()
								+ "); se usa la conciliacion."));
			}

			ReservaGuarderia.TipoEstadia tipoEstadia = ParseoDatos.modalidad(modalidadTexto);
			if (modalidadTexto.isBlank()) {
				incidencias.add(new PlanImportacion.Incidencia(
						PlanImportacion.TipoIncidencia.AVISO, hoja.nombre(), numeroFila,
						"Modalidad vacia; se carga como DIURNA."));
			}

			List<PlanImportacion.PagoPlan> pagos = pagosDe(conciliacion, ingreso, LocalTime.of(10, 0));
			BigDecimal sena = conciliacion.sena();
			if (sena.compareTo(BigDecimal.ZERO) > 0) {
				transformaciones.add("Reserva " + nroReserva + ": la senia de " + sena.toPlainString()
						+ " se imputa como pago SENA y queda registrada en reserva.sena.");
			}

			String observaciones = comportamiento.isBlank() ? null : comportamiento;
			if (observaciones != null && observaciones.length() > 300) {
				observaciones = observaciones.substring(0, 300);
				incidencias.add(new PlanImportacion.Incidencia(
						PlanImportacion.TipoIncidencia.AVISO, hoja.nombre(), numeroFila,
						"Comportamiento_Cuidados supera 300 caracteres; se recorta."));
			}

			guarderias.add(new PlanImportacion.GuarderiaPlan(
					numero,
					nroReserva,
					ingreso,
					egreso,
					mascota.tutorDni,
					mascota.tutorNombre,
					mascota.nombre,
					modalidadTexto,
					tipoEstadia,
					conciliacion.total(),
					sena,
					pagos,
					observaciones,
					conciliacion.nota()));
		}
	}

	// ------------------------------------------------------------- utilidades

	private static List<PlanImportacion.PagoPlan> pagosDe(
			ConciliacionHistorica.Conciliacion conciliacion, LocalDate fecha, LocalTime hora) {
		List<PlanImportacion.PagoPlan> pagos = new ArrayList<>();
		for (ConciliacionHistorica.PagoConciliado pago : conciliacion.pagos()) {
			LocalDateTime fechaHora = pago.metodo() == Pago.MetodoPago.SENA
					? fecha.atTime(9, 0)
					: fecha.atTime(hora);
			pagos.add(new PlanImportacion.PagoPlan(
					pago.metodo(), pago.monto(), fechaHora, pago.nota()));
		}
		return List.copyOf(pagos);
	}

	private static MascotaBuilder buscarMascota(
			Map<String, MascotaBuilder> mascotasPorClave, String tutor, String mascota) {
		return mascotasPorClave.get(mascotaClave(
				ParseoDatos.claveNombre(tutor), ParseoDatos.claveNombre(mascota)));
	}

	private static String mascotaClave(String tutorClave, String mascotaClave) {
		return tutorClave + "|" + mascotaClave;
	}

	private static String matriculaPlaceholder(String[] nombreApellido) {
		String completo = (nombreApellido[0] + " " + nombreApellido[1]).trim();
		String sinAcentos = Normalizer.normalize(completo, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
		return "HIST-" + sinAcentos.toUpperCase(Locale.ROOT).replaceAll("\\s+", "-");
	}

	private static PlanImportacion.Incidencia error(
			LectorExcel.HojaExcel hoja, int fila, String mensaje) {
		return new PlanImportacion.Incidencia(
				PlanImportacion.TipoIncidencia.ERROR, hoja.nombre(), fila, mensaje);
	}

	private static Map<String, Integer> columnas(LectorExcel.HojaExcel hoja) {
		Map<String, Integer> columnas = new HashMap<>();
		if (hoja.filas().isEmpty()) {
			return columnas;
		}
		List<String> encabezado = hoja.filas().get(0);
		for (int indice = 0; indice < encabezado.size(); indice++) {
			columnas.put(encabezado.get(indice).trim().toLowerCase(Locale.ROOT), indice);
		}
		return columnas;
	}

	private static boolean tieneColumnas(Map<String, Integer> columnas, String... requeridas) {
		for (String requerida : requeridas) {
			if (!columnas.containsKey(requerida.toLowerCase(Locale.ROOT))) {
				return false;
			}
		}
		return true;
	}

	private static String celda(List<String> fila, Map<String, Integer> columnas, String nombre) {
		Integer indice = columnas.get(nombre.toLowerCase(Locale.ROOT));
		if (indice == null || indice < 0 || indice >= fila.size()) {
			return "";
		}
		String valor = fila.get(indice);
		return valor == null ? "" : valor.trim();
	}

	private static boolean filaVacia(List<String> fila) {
		return fila.stream().allMatch(valor -> valor == null || valor.isBlank());
	}

	private static List<String> camposSinDestino() {
		return List.of(
				"[Hoja 1] ID_Registro: no existe campo equivalente en tutor/mascota; "
						+ "solo se usa para trazabilidad en este reporte.",
				"[Hoja 1] Edad_Aprox: no se almacena; se convierte a fechaNacimiento estimada.",
				"[Hoja 1] Especie_Raza: se descompone en especie + raza; el texto original no se guarda.",
				"[Hoja 1] Direccion_Localidad: se guarda completo en Tutor.direccion; "
						+ "no hay campo separado de localidad.",
				"[Hoja 1] La planilla no trae DNI, email, chip, sexo, conducta ni cotutores: "
						+ "se usan DNIs provisionales y los demas campos quedan null.",
				"[Hoja 2] Nro_Ficha: no existe campo en consulta_medica; solo se usa en el reporte.",
				"[Hoja 2] Tipo_Servicio: no hay catalogo de servicios; se guarda como descripcion "
						+ "del item de la comanda.",
				"[Hoja 2] Detalle_Clinico_y_Cobro: mezcla anamnesis, diagnostico, tratamiento y cobro. "
						+ "Se guarda completo en anamnesis; diagnostico y tratamiento quedan null.",
				"[Hoja 2] La planilla no trae hora de atencion ni peso: se usa 10:00 y pesoKg null.",
				"[Hoja 3] Nro_Reserva: no existe campo en reserva_guarderia; solo se usa en el reporte.",
				"[Hoja 3] Modalidad: se mapea al enum TipoEstadia; el texto original no se guarda.",
				"[Hoja 3] Estado_Pago: se convierte en pagos + saldo; no hay campo de estado de pago.",
				"[Hoja 3] Comportamiento_Cuidados: se guarda en reserva.observaciones.",
				"[Hoja 3] La planilla no trae horas de ingreso/egreso: horaEntrada y horaSalida "
						+ "quedan null.");
	}

	// ------------------------------------------------------------- builders

	private static final class TutorBuilder {
		private String dni;
		private String nombre;
		private String apellido;
		private String telefono;
		private String email;
		private String direccion;
		private boolean telefonoProvisorio;
		private final List<Integer> filas = new ArrayList<>();
	}

	private static final class MascotaBuilder {
		private String tutorDni;
		private String tutorNombre;
		private String nombre;
		private Mascota.Especie especie;
		private String raza;
		private LocalDate fechaNacimiento;
		private BigDecimal pesoKg;
		private Integer edadMeses;
		private String edadTexto;
		private String especieRazaTexto;
		private final List<Integer> filas = new ArrayList<>();
	}

	private static final class ProfesionalBuilder {
		private String nombre;
		private String apellido;
		private String matricula;
		private String origen;
	}
}
