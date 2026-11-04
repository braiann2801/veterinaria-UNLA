package com.veterinaria.veterinaria_backend.importacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifica el plan contra el Excel real de Huellitas.
 *
 * <p>El archivo vive en {@code datos/}, que esta gitignoreado por contener
 * datos personales. Si no existe (CI, clon sin datos), la prueba se salta; en
 * la maquina donde se corre la importacion valida el mapeo completo.</p>
 */
class ConstructorPlanTest {

	private static final Path ARCHIVO = Path.of(
			"..", "datos", "Caso 3 Veterinaria_Huellitas_Datos_Historicos.xlsx");

	@Test
	@DisplayName("El Excel real produce 9 tutores, 11 mascotas, 10 consultas y 10 estadias")
	void planDelExcelReal() throws Exception {
		Assumptions.assumeTrue(Files.isRegularFile(ARCHIVO),
				"No esta el Excel historico en datos/: se omite la prueba de integracion.");

		LectorExcel.LibroExcel libro = new LectorExcel().leer(ARCHIVO);
		PlanImportacion plan = new ConstructorPlan().construir(libro);

		assertTrue(plan.erroresFatales().isEmpty(), () -> String.join(" | ", plan.erroresFatales()));
		assertEquals(9, plan.tutores().size());
		assertEquals(11, plan.mascotas().size());
		assertEquals(2, plan.profesionales().size());
		assertEquals(10, plan.consultas().size());
		assertEquals(10, plan.guarderias().size());

		PlanImportacion.TutorPlan valeria = plan.tutores().get(0);
		assertEquals("00000001", valeria.dni());
		assertEquals("Valeria", valeria.nombre());
		assertEquals("Gomez", valeria.apellido());
		assertEquals("1144558899", valeria.telefono());

		PlanImportacion.TutorPlan esteban = plan.tutores().stream()
				.filter(tutor -> tutor.apellido().equals("Morales"))
				.findFirst()
				.orElseThrow();
		assertEquals("1166772233", esteban.telefono());

		PlanImportacion.TutorPlan romina = plan.tutores().stream()
				.filter(tutor -> tutor.apellido().equals("Gaetani"))
				.findFirst()
				.orElseThrow();
		assertEquals("3415443322", romina.telefono());

		PlanImportacion.TutorPlan pablo = plan.tutores().stream()
				.filter(tutor -> tutor.apellido().equals("Echarri"))
				.findFirst()
				.orElseThrow();
		assertEquals("0000000001", pablo.telefono());

		PlanImportacion.MascotaPlan firulais = plan.mascotas().stream()
				.filter(mascota -> mascota.nombre().equals("Firulais"))
				.findFirst()
				.orElseThrow();
		assertEquals(LocalDate.now().minusMonths(48), firulais.fechaNacimiento());
		assertEquals(0, new BigDecimal("28.50").compareTo(firulais.pesoKg()));

		PlanImportacion.ConsultaPlan ficha503 = plan.consultas().stream()
				.filter(consulta -> consulta.numero() == 503)
				.findFirst()
				.orElseThrow();
		assertEquals(0, new BigDecimal("10000.00").compareTo(ficha503.saldo()));

		PlanImportacion.GuarderiaPlan reserva101 = plan.guarderias().stream()
				.filter(guarderia -> guarderia.numero() == 101)
				.findFirst()
				.orElseThrow();
		assertEquals(0, new BigDecimal("20000.00").compareTo(reserva101.sena()));
		assertEquals(0, new BigDecimal("25000.00").compareTo(reserva101.saldo()));
		assertEquals("No juntar con perros machos chicos", reserva101.observaciones());

		assertNotNull(plan.profesionales().stream()
				.filter(profesional -> profesional.matricula().equals("HIST-LAURA"))
				.findFirst()
				.orElse(null));
	}
}
