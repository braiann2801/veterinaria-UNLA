package com.veterinaria.veterinaria_backend.importacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.entity.ComandaItem;
import com.veterinaria.veterinaria_backend.entity.ConsultaMedica;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.Pago;
import com.veterinaria.veterinaria_backend.entity.Profesional;
import com.veterinaria.veterinaria_backend.entity.ReservaGuarderia;
import com.veterinaria.veterinaria_backend.entity.Tutor;
import com.veterinaria.veterinaria_backend.entity.TutorMascota;
import com.veterinaria.veterinaria_backend.repository.ComandaCobroRepository;
import com.veterinaria.veterinaria_backend.repository.ConsultaMedicaRepository;
import com.veterinaria.veterinaria_backend.repository.MascotaRepository;
import com.veterinaria.veterinaria_backend.repository.ProfesionalRepository;
import com.veterinaria.veterinaria_backend.repository.ReservaGuarderiaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorMascotaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorRepository;

import lombok.RequiredArgsConstructor;

/**
 * Escritor transaccional del plan de importacion.
 *
 * <p>Es una via exclusiva de la importacion historica: escribe las entidades
 * directamente porque los servicios normales calculan los importes con la
 * tarifa vigente y no permiten fijar fechas historicas de emision ni de pago.
 * Las reglas de negocio se respetan igual: se usa
 * {@link ComandaCobro#registrarPago(Pago)} para que el saldo y el estado se
 * deriven de los pagos, y las estadias historicas se guardan FINALIZADO sin
 * pasar por el egreso normal (que bloquea deudas pendientes).</p>
 *
 * <p>Todo el plan se aplica en una sola transaccion: si una fila falla, no
 * queda media importacion escrita.</p>
 */
@Service
@RequiredArgsConstructor
public class ImportacionService {

	private static final String DESCRIPCION_GUARDERIA = "Guarderia";

	private final TutorRepository tutores;
	private final MascotaRepository mascotas;
	private final TutorMascotaRepository vinculos;
	private final ProfesionalRepository profesionales;
	private final ConsultaMedicaRepository consultas;
	private final ReservaGuarderiaRepository reservas;
	private final ComandaCobroRepository comandas;

	// --------------------------------------------------------------- simulacion

	/**
	 * Verifica el plan contra la base sin escribir nada. Sirve para que el
	 * reporte de simulacion avise que ya existe y que se omitiria.
	 */
	@Transactional(readOnly = true)
	public ResultadoImportacion simular(PlanImportacion plan) {
		int tutoresExistentes = 0;
		int mascotasExistentes = 0;
		int profesionalesExistentes = 0;
		int consultasOmitidas = 0;
		int guarderiasOmitidas = 0;

		Map<String, Tutor> tutoresPorDni = new LinkedHashMap<>();
		for (PlanImportacion.TutorPlan tutorPlan : plan.tutores()) {
			Optional<Tutor> existente = tutores.findByDni(tutorPlan.dni());
			if (existente.isPresent()) {
				tutoresExistentes++;
				tutoresPorDni.put(tutorPlan.dni(), existente.get());
			}
		}

		Map<String, Mascota> mascotasPorClave = new LinkedHashMap<>();
		for (PlanImportacion.MascotaPlan mascotaPlan : plan.mascotas()) {
			Tutor tutor = tutoresPorDni.get(mascotaPlan.tutorDni());
			if (tutor == null) {
				continue;
			}
			Optional<Mascota> existente = buscarMascotaDelTutor(tutor, mascotaPlan.nombre());
			if (existente.isPresent()) {
				mascotasExistentes++;
				mascotasPorClave.put(claveMascota(mascotaPlan.tutorDni(), mascotaPlan.nombre()),
						existente.get());
			}
		}

		for (PlanImportacion.ProfesionalPlan profesionalPlan : plan.profesionales()) {
			if (profesionales.findByMatricula(profesionalPlan.matricula()).isPresent()) {
				profesionalesExistentes++;
			}
		}

		for (PlanImportacion.ConsultaPlan consultaPlan : plan.consultas()) {
			Mascota mascota = mascotasPorClave.get(
					claveMascota(consultaPlan.tutorDni(), consultaPlan.mascotaNombre()));
			if (mascota != null && consultas.existsByMascotaIdAndFechaAtencion(
					mascota.getId(), consultaPlan.fecha().atTime(10, 0))) {
				consultasOmitidas++;
			}
		}

		for (PlanImportacion.GuarderiaPlan guarderiaPlan : plan.guarderias()) {
			Mascota mascota = mascotasPorClave.get(
					claveMascota(guarderiaPlan.tutorDni(), guarderiaPlan.mascotaNombre()));
			if (mascota != null && reservas.existsByMascotaIdAndFecha(
					mascota.getId(), guarderiaPlan.ingreso())) {
				guarderiasOmitidas++;
			}
		}

		return new ResultadoImportacion(0, tutoresExistentes, 0, mascotasExistentes, 0,
				0, profesionalesExistentes, 0, consultasOmitidas, 0, guarderiasOmitidas, 0, 0);
	}

	// ------------------------------------------------------------------ aplicado

	/** Aplica el plan completo en una sola transaccion. */
	@Transactional
	public ResultadoImportacion aplicar(PlanImportacion plan) {
		int tutoresCreados = 0;
		int tutoresExistentes = 0;
		int mascotasCreadas = 0;
		int mascotasExistentes = 0;
		int vinculosCreados = 0;
		int profesionalesCreados = 0;
		int profesionalesExistentes = 0;
		int consultasCreadas = 0;
		int consultasOmitidas = 0;
		int guarderiasCreadas = 0;
		int guarderiasOmitidas = 0;
		int comandasCreadas = 0;
		int pagosCreados = 0;

		Map<String, Tutor> tutoresPorDni = new LinkedHashMap<>();
		for (PlanImportacion.TutorPlan tutorPlan : plan.tutores()) {
			Optional<Tutor> existente = tutores.findByDni(tutorPlan.dni());
			if (existente.isPresent()) {
				Tutor tutor = existente.get();
				completarTutor(tutor, tutorPlan);
				tutoresPorDni.put(tutorPlan.dni(), tutor);
				tutoresExistentes++;
			} else {
				Tutor tutor = new Tutor();
				tutor.setDni(tutorPlan.dni());
				tutor.setNombre(tutorPlan.nombre());
				tutor.setApellido(tutorPlan.apellido());
				tutor.setTelefono(tutorPlan.telefono());
				tutor.setEmail(tutorPlan.email());
				tutor.setDireccion(tutorPlan.direccion());
				tutor.setActivo(true);
				tutor.setVinculos(new ArrayList<>());
				tutores.save(tutor);
				tutoresPorDni.put(tutorPlan.dni(), tutor);
				tutoresCreados++;
			}
		}

		Map<String, Mascota> mascotasPorClave = new LinkedHashMap<>();
		for (PlanImportacion.MascotaPlan mascotaPlan : plan.mascotas()) {
			Tutor tutor = tutoresPorDni.get(mascotaPlan.tutorDni());
			if (tutor == null) {
				throw new IllegalStateException("No se creo el tutor " + mascotaPlan.tutorDni()
						+ " para la mascota " + mascotaPlan.nombre());
			}

			Optional<Mascota> existente = buscarMascotaDelTutor(tutor, mascotaPlan.nombre());
			Mascota mascota;
			if (existente.isPresent()) {
				mascota = existente.get();
				completarMascota(mascota, mascotaPlan);
				mascotasExistentes++;
			} else {
				mascota = new Mascota();
				mascota.setNombre(mascotaPlan.nombre());
				mascota.setEspecie(mascotaPlan.especie());
				mascota.setRaza(mascotaPlan.raza());
				mascota.setFechaNacimiento(mascotaPlan.fechaNacimiento());
				mascota.setPesoKg(mascotaPlan.pesoKg());
				mascota.setActivo(true);
				mascota.setVinculos(new ArrayList<>());
				mascotas.save(mascota);
				mascotasCreadas++;
			}

			if (vinculos.findByTutorIdAndMascotaId(tutor.getId(), mascota.getId()).isEmpty()) {
				TutorMascota vinculo = new TutorMascota(tutor, mascota, true);
				vinculo.setVinculadoDesde(LocalDateTime.now());
				tutor.getVinculos().add(vinculo);
				mascota.getVinculos().add(vinculo);
				vinculosCreados++;
			}

			mascotasPorClave.put(claveMascota(mascotaPlan.tutorDni(), mascotaPlan.nombre()), mascota);
		}

		Map<String, Profesional> profesionalesPorMatricula = new LinkedHashMap<>();
		for (PlanImportacion.ProfesionalPlan profesionalPlan : plan.profesionales()) {
			Optional<Profesional> existente = profesionales.findByMatricula(profesionalPlan.matricula());
			if (existente.isPresent()) {
				profesionalesPorMatricula.put(profesionalPlan.matricula(), existente.get());
				profesionalesExistentes++;
			} else {
				Profesional profesional = new Profesional();
				profesional.setNombre(profesionalPlan.nombre());
				profesional.setApellido(profesionalPlan.apellido());
				profesional.setMatricula(profesionalPlan.matricula());
				profesional.setActivo(true);
				profesionales.save(profesional);
				profesionalesPorMatricula.put(profesionalPlan.matricula(), profesional);
				profesionalesCreados++;
			}
		}

		for (PlanImportacion.ConsultaPlan consultaPlan : plan.consultas()) {
			Tutor tutor = tutoresPorDni.get(consultaPlan.tutorDni());
			Mascota mascota = mascotasPorClave.get(
					claveMascota(consultaPlan.tutorDni(), consultaPlan.mascotaNombre()));
			Profesional profesional = profesionalesPorMatricula.get(consultaPlan.matriculaProfesional());
			if (tutor == null || mascota == null || profesional == null) {
				throw new IllegalStateException("No se resolvio la ficha " + consultaPlan.nroFicha()
						+ " (tutor, mascota o profesional faltante).");
			}

			LocalDateTime fechaAtencion = consultaPlan.fecha().atTime(10, 0);
			if (consultas.existsByMascotaIdAndFechaAtencion(mascota.getId(), fechaAtencion)) {
				consultasOmitidas++;
				continue;
			}

			ConsultaMedica consulta = new ConsultaMedica();
			consulta.setMascota(mascota);
			consulta.setProfesional(profesional);
			consulta.setFechaAtencion(fechaAtencion);
			consulta.setAnamnesis(truncar(consultaPlan.detalle(), 2000));
			consulta.setDiagnostico(null);
			consulta.setTratamiento(null);
			consulta.setPesoKg(null);
			consulta.setConductaObservada(consultaPlan.conducta());
			consultas.save(consulta);

			ComandaCobro comanda = nuevaComanda(
					ComandaCobro.Concepto.CONSULTA_CLINICA,
					consultaPlan.total(),
					tutor,
					mascota,
					consultaPlan.fecha().atTime(10, 30));
			comanda.setConsultaMedica(consulta);
			agregarItem(comanda,
					consultaPlan.tipoServicio().isBlank() ? "Consulta clinica" : consultaPlan.tipoServicio(),
					consultaPlan.total());
			agregarPagos(comanda, consultaPlan.pagos(), tutor);
			comandas.save(comanda);

			consultasCreadas++;
			comandasCreadas++;
			pagosCreados += consultaPlan.pagos().size();
		}

		for (PlanImportacion.GuarderiaPlan guarderiaPlan : plan.guarderias()) {
			Tutor tutor = tutoresPorDni.get(guarderiaPlan.tutorDni());
			Mascota mascota = mascotasPorClave.get(
					claveMascota(guarderiaPlan.tutorDni(), guarderiaPlan.mascotaNombre()));
			if (tutor == null || mascota == null) {
				throw new IllegalStateException("No se resolvio la reserva " + guarderiaPlan.nroReserva()
						+ " (tutor o mascota faltante).");
			}

			if (reservas.existsByMascotaIdAndFecha(mascota.getId(), guarderiaPlan.ingreso())) {
				guarderiasOmitidas++;
				continue;
			}

			ReservaGuarderia reserva = new ReservaGuarderia();
			reserva.setFecha(guarderiaPlan.ingreso());
			reserva.setFechaSalida(guarderiaPlan.egreso().equals(guarderiaPlan.ingreso())
					? null
					: guarderiaPlan.egreso());
			reserva.setTipoEstadia(guarderiaPlan.tipoEstadia());
			reserva.setEstado(ReservaGuarderia.EstadoReserva.FINALIZADO);
			reserva.setMascota(mascota);
			reserva.setSena(guarderiaPlan.sena().compareTo(BigDecimal.ZERO) > 0
					? guarderiaPlan.sena()
					: null);
			reserva.setObservaciones(guarderiaPlan.observaciones());
			reservas.save(reserva);

			ComandaCobro comanda = nuevaComanda(
					ComandaCobro.Concepto.GUARDERIA,
					guarderiaPlan.total(),
					tutor,
					mascota,
					guarderiaPlan.ingreso().atTime(9, 0));
			comanda.setReservaGuarderia(reserva);
			agregarItem(comanda,
					DESCRIPCION_GUARDERIA + (guarderiaPlan.modalidadTexto().isBlank()
							? ""
							: " - " + guarderiaPlan.modalidadTexto()),
					guarderiaPlan.total());
			agregarPagos(comanda, guarderiaPlan.pagos(), tutor);
			comandas.save(comanda);

			guarderiasCreadas++;
			comandasCreadas++;
			pagosCreados += guarderiaPlan.pagos().size();
		}

		return new ResultadoImportacion(
				tutoresCreados,
				tutoresExistentes,
				mascotasCreadas,
				mascotasExistentes,
				vinculosCreados,
				profesionalesCreados,
				profesionalesExistentes,
				consultasCreadas,
				consultasOmitidas,
				guarderiasCreadas,
				guarderiasOmitidas,
				comandasCreadas,
				pagosCreados);
	}

	// ---------------------------------------------------------------- internos

	private Optional<Mascota> buscarMascotaDelTutor(Tutor tutor, String nombre) {
		return mascotas.findByNombreContainingIgnoreCase(nombre).stream()
				.filter(mascota -> mascota.getNombre().equalsIgnoreCase(nombre))
				.filter(mascota -> vinculos.findByTutorIdAndMascotaId(
						tutor.getId(), mascota.getId()).isPresent())
				.findFirst();
	}

	private void completarTutor(Tutor tutor, PlanImportacion.TutorPlan plan) {
		if (estaVacio(tutor.getNombre())) {
			tutor.setNombre(plan.nombre());
		}
		if (estaVacio(tutor.getApellido())) {
			tutor.setApellido(plan.apellido());
		}
		if (estaVacio(tutor.getTelefono())) {
			tutor.setTelefono(plan.telefono());
		}
		if (estaVacio(tutor.getEmail()) && !estaVacio(plan.email())) {
			tutor.setEmail(plan.email());
		}
		if (estaVacio(tutor.getDireccion()) && !estaVacio(plan.direccion())) {
			tutor.setDireccion(plan.direccion());
		}
	}

	private void completarMascota(Mascota mascota, PlanImportacion.MascotaPlan plan) {
		if (estaVacio(mascota.getNombre())) {
			mascota.setNombre(plan.nombre());
		}
		if (mascota.getEspecie() == null) {
			mascota.setEspecie(plan.especie());
		}
		if (estaVacio(mascota.getRaza()) && !estaVacio(plan.raza())) {
			mascota.setRaza(plan.raza());
		}
		if (mascota.getFechaNacimiento() == null) {
			mascota.setFechaNacimiento(plan.fechaNacimiento());
		}
		if (mascota.getPesoKg() == null) {
			mascota.setPesoKg(plan.pesoKg());
		}
	}

	private ComandaCobro nuevaComanda(
			ComandaCobro.Concepto concepto,
			BigDecimal monto,
			Tutor tutor,
			Mascota mascota,
			LocalDateTime fechaEmision) {
		BigDecimal total = monto.setScale(2, RoundingMode.HALF_UP);
		ComandaCobro comanda = new ComandaCobro();
		comanda.setConcepto(concepto);
		comanda.setMontoTotal(total);
		comanda.setSaldoPendiente(total);
		comanda.setEstado(ComandaCobro.EstadoComanda.PENDIENTE);
		comanda.setTutor(tutor);
		comanda.setMascota(mascota);
		comanda.setFechaEmision(fechaEmision);
		return comanda;
	}

	private void agregarItem(ComandaCobro comanda, String descripcion, BigDecimal monto) {
		BigDecimal total = monto.setScale(2, RoundingMode.HALF_UP);
		ComandaItem item = new ComandaItem();
		item.setDescripcion(truncar(descripcion, 80));
		item.setCantidad(1);
		item.setPrecioUnitario(total);
		item.setSubtotal(total);
		comanda.agregarItem(item);
	}

	private void agregarPagos(ComandaCobro comanda, List<PlanImportacion.PagoPlan> pagos, Tutor tutor) {
		for (PlanImportacion.PagoPlan pagoPlan : pagos) {
			Pago pago = new Pago();
			pago.setComanda(comanda);
			pago.setMetodoPago(pagoPlan.metodo());
			pago.setMonto(pagoPlan.monto());
			pago.setFechaHora(pagoPlan.fechaHora());
			pago.setTutorPagador(tutor);
			comanda.registrarPago(pago);
		}
	}

	private static String claveMascota(String tutorDni, String nombreMascota) {
		return tutorDni + "|" + ParseoDatos.claveNombre(nombreMascota);
	}

	private static boolean estaVacio(String valor) {
		return valor == null || valor.isBlank();
	}

	private static String truncar(String valor, int maximo) {
		if (valor == null) {
			return null;
		}
		return valor.length() <= maximo ? valor : valor.substring(0, maximo);
	}
}
