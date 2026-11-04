package com.veterinaria.veterinaria_backend.importacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.veterinaria.veterinaria_backend.entity.ConsultaMedica;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.Pago;
import com.veterinaria.veterinaria_backend.entity.ReservaGuarderia;

/**
 * Plan completo de importacion, construido antes de tocar la base.
 *
 * <p>Es el contrato entre el lector del Excel, el reporte y el escritor. En
 * modo simulacion se genera igual que en modo aplicado: la unica diferencia es
 * que {@link ImportacionService#aplicar(PlanImportacion)} no se ejecuta.</p>
 */
public record PlanImportacion(
		List<TutorPlan> tutores,
		List<MascotaPlan> mascotas,
		List<ProfesionalPlan> profesionales,
		List<ConsultaPlan> consultas,
		List<GuarderiaPlan> guarderias,
		List<Incidencia> incidencias,
		List<String> transformaciones,
		List<String> camposSinDestino,
		List<String> erroresFatales) {

	public record TutorPlan(
			String dni,
			String nombre,
			String apellido,
			String telefono,
			String email,
			String direccion,
			List<Integer> filas) {
	}

	public record MascotaPlan(
			String tutorDni,
			String tutorNombre,
			String nombre,
			Mascota.Especie especie,
			String raza,
			LocalDate fechaNacimiento,
			BigDecimal pesoKg,
			Integer edadMeses,
			String edadTexto,
			String especieRazaTexto,
			List<Integer> filas) {
	}

	public record ProfesionalPlan(
			String nombre,
			String apellido,
			String matricula,
			String origen) {
	}

	public record PagoPlan(
			Pago.MetodoPago metodo,
			BigDecimal monto,
			LocalDateTime fechaHora,
			String nota) {
	}

	public record ConsultaPlan(
			int numero,
			String nroFicha,
			LocalDate fecha,
			String tutorDni,
			String tutorNombre,
			String mascotaNombre,
			String tipoServicio,
			String detalle,
			String matriculaProfesional,
			ConsultaMedica.ConductaObservada conducta,
			BigDecimal total,
			List<PagoPlan> pagos,
			String conciliacionNota) {

		public BigDecimal pagado() {
			return sumar(pagos);
		}

		public BigDecimal saldo() {
			return total.subtract(pagado()).setScale(2, RoundingMode.HALF_UP);
		}
	}

	public record GuarderiaPlan(
			int numero,
			String nroReserva,
			LocalDate ingreso,
			LocalDate egreso,
			String tutorDni,
			String tutorNombre,
			String mascotaNombre,
			String modalidadTexto,
			ReservaGuarderia.TipoEstadia tipoEstadia,
			BigDecimal total,
			BigDecimal sena,
			List<PagoPlan> pagos,
			String observaciones,
			String conciliacionNota) {

		public BigDecimal pagado() {
			return sumar(pagos);
		}

		public BigDecimal saldo() {
			return total.subtract(pagado()).setScale(2, RoundingMode.HALF_UP);
		}
	}

	public enum TipoIncidencia {
		ERROR,
		CONFLICTO,
		AVISO
	}

	public record Incidencia(
			TipoIncidencia tipo,
			String hoja,
			int fila,
			String mensaje) {
	}

	private static BigDecimal sumar(List<PagoPlan> pagos) {
		return pagos.stream()
				.map(PagoPlan::monto)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(2, RoundingMode.HALF_UP);
	}
}
