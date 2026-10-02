package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.MascotaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.ReservaGuarderiaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ReservaGuarderiaResponseDTO;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.service.MascotaService;
import com.veterinaria.veterinaria_backend.service.ReservaGuarderiaService;

/**
 * TASK-008: horarios de entrada y salida en la reserva de guardería.
 *
 * <p>El foco es que el par de horas se guarde, se devuelva y no rompa la regla
 * 2.1 de AGENTS.md: agregar el horario no puede alterar el conteo de aforo.
 * Cada caso va en su propio dia para que el test sea independiente del orden.
 * </p>
 */
@SpringBootTest
@Transactional
class ReservaGuarderiaHorarioTest {

	private static final LocalDate DIA = LocalDate.of(2026, 11, 17);

	@Autowired
	private ReservaGuarderiaService guarderiaService;

	@Autowired
	private MascotaService mascotaService;

	@Test
	@DisplayName("Una reserva con horario devuelve las dos horas y las persiste")
	void persisteElHorarioIndicado() {
		ReservaGuarderiaResponseDTO reserva = guarderiaService.crear(
				pedir(crearMascota("Roco"), LocalTime.of(8, 30), LocalTime.of(18, 0)));

		assertThat(reserva.horaEntrada()).isEqualTo(LocalTime.of(8, 30));
		assertThat(reserva.horaSalida()).isEqualTo(LocalTime.of(18, 0));

		// Relectura desde la base: si el mapper llenara el DTO a mano en vez de
		// leer la entidad, esta asercion fallaria.
		var releida = guarderiaService.listarPorFecha(DIA).stream()
				.filter(r -> r.id().equals(reserva.id()))
				.findFirst()
				.orElseThrow();

		assertThat(releida.horaEntrada()).isEqualTo(LocalTime.of(8, 30));
		assertThat(releida.horaSalida()).isEqualTo(LocalTime.of(18, 0));
	}

	@Test
	@DisplayName("Una reserva sin horario sigue siendo valida (retrocompatible)")
	void sinHorarioSeAcepta() {
		ReservaGuarderiaResponseDTO reserva = guarderiaService.crear(pedir(crearMascota("Tita"), null, null));

		assertThat(reserva.horaEntrada()).isNull();
		assertThat(reserva.horaSalida()).isNull();
	}

	@Test
	@DisplayName("La salida debe ser posterior a la entrada")
	void laSalidaNoPuedeSerPreviaALaEntrada() {
		long mascotaId = crearMascota("Firulais");

		assertThatThrownBy(() -> guarderiaService.crear(
				pedir(mascotaId, LocalTime.of(18, 0), LocalTime.of(9, 0))))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("posterior a la de entrada");
	}

	@Test
	@DisplayName("En estadia diurna, salida igual a entrada se rechaza: duracion cero es un error de tipeo")
	void duracionCeroSeRechazaEnEstadiaCorta() {
		long mascotaId = crearMascota("Bruno");

		// El helper por defecto arma DIURNA, que es donde la igualdad no tiene
		// lectura posible. En COMPLETA_24H el mismo par es valido: lo cubre
		// estadiaCompletaCruzaLaMedianoche.
		assertThatThrownBy(() -> guarderiaService.crear(
				pedir(mascotaId, LocalTime.of(9, 0), LocalTime.of(9, 0))))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("posterior a la de entrada");
	}

	@Test
	@DisplayName("En estadia diurna la hora de salida anterior a la de entrada se rechaza")
	void laSalidaNoPuedeSerPreviaEnEstadiaCorta() {
		long mascotaId = crearMascota("Pipa");

		assertThatThrownBy(() -> guarderiaService.crear(
				pedir(mascotaId, LocalTime.of(18, 0), LocalTime.of(9, 0))))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("posterior a la de entrada");
	}

	@Test
	@DisplayName("Una reserva COMPLETA_24H con salida anterior en el reloj igual se acepta: cruza a otro dia")
	void estadiaCompletaAceptaSalidaAnteriorEnElReloj() {
		// 22:00 -> 06:00 parece "salida antes que entrada" pero son las 06:00
		// del dia siguiente: la mas comun de las_COMPLETA_24H. Sin mirar el tipo
		// de estadia la validacion la rechazaria.
		ReservaGuarderiaResponseDTO reserva = guarderiaService.crear(
				pedir(crearMascota("Ares"), LocalTime.of(22, 0), LocalTime.of(6, 0), true));

		assertThat(reserva.horaEntrada()).isEqualTo(LocalTime.of(22, 0));
		assertThat(reserva.horaSalida()).isEqualTo(LocalTime.of(6, 0));
	}

	@Test
	@DisplayName("Un horario a medias se rechaza en vez de inventar la hora complementaria")
	void elHorarioVaCompletoONoVa() {
		long mascotaId = crearMascota("Canela");

		assertThatThrownBy(() -> guarderiaService.crear(pedir(mascotaId, LocalTime.of(9, 0), null)))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("juntas");

		assertThatThrownBy(() -> guarderiaService.crear(pedir(mascotaId, null, LocalTime.of(18, 0))))
				.isInstanceOf(BadRequestException.class)
				.hasMessageContaining("juntas");
	}

	@Test
	@DisplayName("Estadia de 24h admite entrada 08:00 y salida 08:00 del dia siguiente")
	void estadiaCompletaCruzaLaMedianoche() {
		// La hora de salida es una hora del reloj, no un instante: las 08:00 del
		// dia siguiente se escriben como 08:00. El modelo es LocalTime justamente
		// para que la UI no tenga que restar 24h antes de mostrarla.
		ReservaGuarderiaResponseDTO reserva = guarderiaService.crear(
				pedir(crearMascota("Nube"), LocalTime.of(8, 0), LocalTime.of(8, 0), true));

		assertThat(reserva.tipoEstadia()).isEqualTo("COMPLETA_24H");
		assertThat(reserva.horaEntrada()).isEqualTo(LocalTime.of(8, 0));
		assertThat(reserva.horaSalida()).isEqualTo(LocalTime.of(8, 0));
	}

	@Test
	@DisplayName("El horario ordena la jornada: primero las que tienen hora, de la mas temprano a la mas tarde")
	void elListadoQuedaOrdenadoComoUnaJornada() {
		// Se crean en orden inverso al esperado, con una sin horario en el medio.
		guarderiaService.crear(pedir(crearMascota("Tarde"), LocalTime.of(17, 0), LocalTime.of(20, 0)));
		guarderiaService.crear(pedir(crearMascota("SinHora"), null, null));
		guarderiaService.crear(pedir(crearMascota("Manana"), LocalTime.of(8, 0), LocalTime.of(13, 0)));
		guarderiaService.crear(pedir(crearMascota("Mediodia"), LocalTime.of(13, 0), LocalTime.of(17, 0)));

		assertThat(guarderiaService.listarPorFecha(DIA).stream().map(ReservaGuarderiaResponseDTO::nombreMascota))
				.containsExactly("Manana", "Mediodia", "Tarde", "SinHora");
	}

	@Test
	@DisplayName("El horario no altera el tope de 10 cupos del dia")
	void elHorarioNoCambiaElAforo() {
		for (int i = 0; i < 10; i++) {
			guarderiaService.crear(pedir(crearMascota("Perro" + i),
					LocalTime.of(8, 0).plusMinutes(i * 30L), LocalTime.of(18, 0)));
		}

		assertThat(guarderiaService.consultarAforo(DIA).completo()).isTrue();

		assertThatThrownBy(() -> guarderiaService.crear(
				pedir(crearMascota("Once"), LocalTime.of(8, 0), LocalTime.of(18, 0))))
				.hasMessageContaining("aforo");
	}

	// ------------------------------------------------------------- helpers

	private long crearMascota(String nombre) {
		MascotaRequestDTO request = new MascotaRequestDTO(
				nombre, MascotaRequestDTO.EspecieRequestDTO.CANINO, null, null, null, null, null, null, null, null);
		MascotaResponseDTO mascota = mascotaService.crear(request);
		return mascota.id();
	}

	private ReservaGuarderiaRequestDTO pedir(long mascotaId, LocalTime entrada, LocalTime salida) {
		return pedir(mascotaId, entrada, salida, false);
	}

	private ReservaGuarderiaRequestDTO pedir(long mascotaId, LocalTime entrada, LocalTime salida,
			boolean completa24h) {
		return pedir(mascotaId, entrada, salida, completa24h
				? ReservaGuarderiaRequestDTO.TipoEstadiaRequestDTO.COMPLETA_24H
				: ReservaGuarderiaRequestDTO.TipoEstadiaRequestDTO.DIURNA);
	}

	private ReservaGuarderiaRequestDTO pedir(long mascotaId, LocalTime entrada, LocalTime salida,
			ReservaGuarderiaRequestDTO.TipoEstadiaRequestDTO tipo) {
		return new ReservaGuarderiaRequestDTO(DIA, tipo, mascotaId, entrada, salida, null, null);
	}
}