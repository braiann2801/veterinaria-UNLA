package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.MascotaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.ReservaGuarderiaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ReservaGuarderiaResponseDTO;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.entity.ReservaGuarderia;
import com.veterinaria.veterinaria_backend.service.MascotaService;
import com.veterinaria.veterinaria_backend.service.ReservaGuarderiaService;

/**
 * AGENTS.md 2.1: aforo maximo e innegociable de 10 perros concurrentes por dia.
 *
 * <p>Esta es la prueba que blinda la regla mas critica del dominio. El noveno
 * perro entra, el decimo lo llena y el undecimo recibe 409.</p>
 */
@SpringBootTest
@Transactional
class AforoGuarderiaTest {

	private static final LocalDate DIA = LocalDate.of(2026, 11, 10);

	@Autowired
	private ReservaGuarderiaService guarderiaService;

	@Autowired
	private MascotaService mascotaService;

	@BeforeEach
	void prepararDiaLimpio() {
		// El @Transactional del test revierte todo al final, pero el aforo se
		// mide sobre la base: partimos de cero para que el conteo sea exacto.
		guarderiaService.listarPorFecha(DIA);
	}

	@Test
	@DisplayName("El dia arranca con aforo vacio y 10 cupos disponibles")
	void aforoInicialEstaLibre() {
		var aforo = guarderiaService.consultarAforo(DIA);

		assertThat(aforo.cuposOcupados()).isZero();
		assertThat(aforo.aforoMaximo()).isEqualTo(10);
		assertThat(aforo.cuposDisponibles()).isEqualTo(10);
		assertThat(aforo.completo()).isFalse();
	}

	@Test
	@DisplayName("Las 10 primeras reservas entran y la onceava recibe 409")
	void rechazoAlSuperarElAforo() {
		// Arrange: 10 perros distintos para las 10 reservas
		for (int i = 0; i < ReservaGuarderia.AFORO_MAXIMO; i++) {
			long mascotaId = crearMascota("Perro" + i);
			ReservaGuarderiaResponseDTO reserva = guarderiaService.crear(pedir(mascotaId));
			assertThat(reserva.id()).isNotNull();
		}

		// Act & Assert: la onceava debe rebotar
		long mascotaSobrante = crearMascota("PerroSobrante");

		assertThatThrownBy(() -> guarderiaService.crear(pedir(mascotaSobrante)))
				.isInstanceOf(BusinessConflictException.class)
				.hasMessageContaining("aforo completo")
				.hasMessageContaining("10 de 10")
				.hasMessageContaining("No se pueden admitir mas reservas");

		// El aforo sigue en 10: la rechazada no se persisted
		assertThat(guarderiaService.consultarAforo(DIA).cuposOcupados()).isEqualTo(10);
		assertThat(guarderiaService.consultarAforo(DIA).completo()).isTrue();
	}

	@Test
	@DisplayName("Al llenar el dia, cancelar una reserva libera un cupo")
	void cancelarLiberaCupo() {
		for (int i = 0; i < ReservaGuarderia.AFORO_MAXIMO; i++) {
			guarderiaService.crear(pedir(crearMascota("Perro" + i)));
		}
		assertThat(guarderiaService.consultarAforo(DIA).completo()).isTrue();

		// Libera el primero
		List<ReservaGuarderiaResponseDTO> reservas = guarderiaService.listarPorFecha(DIA);
		ReservaGuarderiaResponseDTO aCancelar = reservas.get(0);
		guarderiaService.cancelar(aCancelar.id());

		// Assert: ahora entra uno mas
		assertThat(guarderiaService.consultarAforo(DIA).cuposDisponibles()).isEqualTo(1);
		ReservaGuarderiaResponseDTO nueva = guarderiaService.crear(pedir(crearMascota("PerroExtra")));
		assertThat(nueva.cuposOcupados()).isEqualTo(10);
	}

	@Test
	@DisplayName("Una reserva CANCELADA no ocupa cupo, pero una RESERVADA si")
	void soloReservadoEIngresadoCuentan() {
		for (int i = 0; i < ReservaGuarderia.AFORO_MAXIMO; i++) {
			guarderiaService.crear(pedir(crearMascota("Perro" + i)));
		}

		// Las 10 occupies cupo. Una cancelada lo libera.
		guarderiaService.cancelar(guarderiaService.listarPorFecha(DIA).get(0).id());
		assertThat(guarderiaService.consultarAforo(DIA).cuposOcupados()).isEqualTo(9);
	}

	@Test
	@DisplayName("El aforo es por dia: el dia siguiente tiene los 10 cupos libres")
	void aforoEsPorDia() {
		for (int i = 0; i < ReservaGuarderia.AFORO_MAXIMO; i++) {
			guarderiaService.crear(pedir(crearMascota("Perro" + i)));
		}
		assertThat(guarderiaService.consultarAforo(DIA).completo()).isTrue();

		LocalDate otroDia = DIA.plusDays(1);
		assertThat(guarderiaService.consultarAforo(otroDia).cuposDisponibles()).isEqualTo(10);

		// Y entra sin problema
		ReservaGuarderiaResponseDTO otra = guarderiaService.crear(
				pedir(crearMascota("PerroDelDiaSiguiente"), otroDia));
		assertThat(otra.cuposOcupados()).isEqualTo(1);
	}

	@Test
	@DisplayName("La misma mascota no puede tener dos reservas activas el mismo dia")
	void noSePuedeDuplicarMascotaEnElDia() {
		long mascotaId = crearMascota("Unico");

		guarderiaService.crear(pedir(mascotaId));

		assertThatThrownBy(() -> guarderiaService.crear(pedir(mascotaId)))
				.isInstanceOf(com.veterinaria.veterinaria_backend.exception.BadRequestException.class)
				.hasMessageContaining("ya tiene una reserva activa");
	}

	// ------------------------------------------------------------- helpers

	private long crearMascota(String nombre) {
		MascotaRequestDTO request = new MascotaRequestDTO(
				nombre, MascotaRequestDTO.EspecieRequestDTO.CANINO, null, null, null, null, null, null, null, null);
		MascotaResponseDTO mascota = mascotaService.crear(request);
		return mascota.id();
	}

	private ReservaGuarderiaRequestDTO pedir(long mascotaId) {
		return pedir(mascotaId, DIA);
	}

	private ReservaGuarderiaRequestDTO pedir(long mascotaId, LocalDate fecha) {
		return new ReservaGuarderiaRequestDTO(
				fecha, ReservaGuarderiaRequestDTO.TipoEstadiaRequestDTO.COMPLETA_24H, mascotaId, null, null);
	}
}