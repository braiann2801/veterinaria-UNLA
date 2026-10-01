package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.MascotaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.ProfesionalRequestDTO;
import com.veterinaria.veterinaria_backend.dto.ProfesionalResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoClinicoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoClinicoResponseDTO;
import com.veterinaria.veterinaria_backend.entity.Profesional;
import com.veterinaria.veterinaria_backend.entity.TurnoClinico;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.repository.ProfesionalRepository;
import com.veterinaria.veterinaria_backend.repository.TurnoClinicoRepository;
import com.veterinaria.veterinaria_backend.service.MascotaService;
import com.veterinaria.veterinaria_backend.service.TurnoClinicoService;

/**
 * AGENTS.md 2.2: bloques de 45 min de atencion seguidos de 15 de desinfeccion.
 *
 * <p>El caso limite es exactamente el minuto 45: la consulta ya termino, pero
 * el consultorio sigue en desinfeccion hasta el minuto 60, asi que un turno que
 * arranque ahi debe rechazarse con 409.</p>
 */
@SpringBootTest
@Transactional
class VentanaDesinfeccionTest {

	private static final LocalDateTime DIA_BASE = LocalDateTime.of(2026, 11, 10, 9, 0);

	@Autowired
	private TurnoClinicoService turnoService;

	@Autowired
	private MascotaService mascotaService;

	@Autowired
	private ProfesionalRepository profesionalRepository;

	@Autowired
	private TurnoClinicoRepository turnoRepository;

	private Long profesionalId;
	private Long mascotaId;

	@BeforeEach
	void prepararActores() {
		profesionalId = crearProfesional();
		mascotaId = crearMascota();
	}

	@Test
	@DisplayName("El turno deriva fin=+45 y finBloque=+60 automaticamente")
	void calculaVentanaDe45YMAS60() {
		TurnoClinicoResponseDTO turno = turnoService.crear(pedir(DIA_BASE));

		assertThat(turno.fechaHoraFin()).isEqualTo(DIA_BASE.plusMinutes(45));
		assertThat(turno.fechaFinBloque()).isEqualTo(DIA_BASE.plusMinutes(60));
	}

	@Test
	@DisplayName("Rechaza con 409 un turno que arranca durante los 15 min de desinfeccion")
	void rechazaTurnoDentroDeLaDesinfeccion() {
		turnoService.crear(pedir(DIA_BASE));

		// El turno previo ocupa de 09:00 a 09:45 (atencion) y bloquea hasta
		// las 10:00 por desinfeccion. Un turno a las 09:45 cae en esa ventana.
		LocalDateTime enDesinfeccion = DIA_BASE.plusMinutes(45);

		assertThatThrownBy(() -> turnoService.crear(pedir(enDesinfeccion)))
				.isInstanceOf(BusinessConflictException.class)
				.hasMessageContaining("ya tiene un turno")
				.hasMessageContaining("desinfeccion")
				.hasMessageContaining("se solapa");
	}

	@Test
	@DisplayName("Rechaza cualquier instante dentro de la ventana de desinfeccion, minuto a minuto")
	void rechazaCadaMinutoDeLaVentana() {
		turnoService.crear(pedir(DIA_BASE));

		// Del minuto 45 al 59 el consultorio sigue en desinfeccion.
		for (int minuto = 45; minuto <= 59; minuto++) {
			LocalDateTime instante = DIA_BASE.plusMinutes(minuto);
			assertThatThrownBy(() -> turnoService.crear(pedir(instante)))
					.as("Deberia rechazar el turno de las %s (minuto %d)", instante.toLocalTime(), minuto)
					.isInstanceOf(BusinessConflictException.class);
		}
	}

	@Test
	@DisplayName("Permite un turno justo cuando termina la desinfeccion (minuto 60)")
	void permiteAlTerminarLaDesinfeccion() {
		turnoService.crear(pedir(DIA_BASE));

		// Minuto 60 = 10:00, la desinfeccion ya termino: entra sin conflicto.
		TurnoClinicoResponseDTO segundo = turnoService.crear(pedir(DIA_BASE.plusMinutes(60)));

		assertThat(segundo.fechaHoraInicio()).isEqualTo(DIA_BASE.plusMinutes(60));
		// 10:00 + 45 de atencion + 15 de desinfeccion = 11:00
		assertThat(segundo.fechaHoraFin()).isEqualTo(DIA_BASE.plusMinutes(105));
		assertThat(segundo.fechaFinBloque()).isEqualTo(DIA_BASE.plusMinutes(120));
	}

	@Test
	@DisplayName("Rechaza un turno que se superpone totalmente con otro")
	void rechazaSolapamientoTotal() {
		turnoService.crear(pedir(DIA_BASE));

		assertThatThrownBy(() -> turnoService.crear(pedir(DIA_BASE.plusMinutes(10))))
				.isInstanceOf(BusinessConflictException.class);
	}

	@Test
	@DisplayName("Dos profesionales distintos pueden tener turnos a la misma hora")
	void distintosProfesionalesNoSeChocan() {
		Long otroProfesionalId = crearProfesional();

		TurnoClinicoResponseDTO turnoA = turnoService.crear(pedir(DIA_BASE));
		TurnoClinicoResponseDTO turnoB = turnoService.crear(
				pedir(DIA_BASE, otroProfesionalId, mascotaId));

		assertThat(turnoA.id()).isNotEqualTo(turnoB.id());
	}

	@Test
	@DisplayName("Cancelar un turno libera su bloque para otro horario")
	void cancelarLiberaElBloque() {
		TurnoClinicoResponseDTO primero = turnoService.crear(pedir(DIA_BASE));
		turnoService.cancelar(primero.id());

		// El bloque cancelado ya no ocupa: entra un turno a las 09:45.
		TurnoClinicoResponseDTO nuevo = turnoService.crear(pedir(DIA_BASE.plusMinutes(45)));
		assertThat(nuevo.id()).isNotNull();
	}

	@Test
	@DisplayName("Reprogramar un turno a una hora ocupada se rechaza con 409")
	void reprogramarAHoraOcupadaChoca() {
		turnoService.crear(pedir(DIA_BASE));
		TurnoClinicoResponseDTO segundo = turnoService.crear(pedir(DIA_BASE.plusMinutes(60)));

		assertThatThrownBy(() -> turnoService.actualizar(segundo.id(),
				pedir(DIA_BASE.plusMinutes(30))))
				.isInstanceOf(BusinessConflictException.class);
	}

	@Test
	@DisplayName("Consultar disponibilidad refleja el mismo criterio que el POST")
	void disponibilidadCoincideConElServicio() {
		assertThat(turnoService.disponible(profesionalId, DIA_BASE)).isTrue();

		turnoService.crear(pedir(DIA_BASE));

		assertThat(turnoService.disponible(profesionalId, DIA_BASE)).isFalse();
		// El minuto 45 sigue bloqueado por desinfeccion.
		assertThat(turnoService.disponible(profesionalId, DIA_BASE.plusMinutes(45))).isFalse();
		// El minuto 60 ya esta libre.
		assertThat(turnoService.disponible(profesionalId, DIA_BASE.plusMinutes(60))).isTrue();
	}

	// ------------------------------------------------------------- helpers

	private Long crearProfesional() {
		ProfesionalRequestDTO request = new ProfesionalRequestDTO(
				"Laura", "Sosa", "MAT-" + System.nanoTime());
		// ProfesionalController es la via HTTP; aca se va directo al repositorio
		// porque el test no necesita validar el endpoint de alta.
		Profesional profesional = new Profesional();
		profesional.setNombre(request.nombre());
		profesional.setApellido(request.apellido());
		profesional.setMatricula(request.matricula());
		profesional.setActivo(true);
		Profesional guardado = profesionalRepository.save(profesional);
		return guardado.getId();
	}

	private Long crearMascota() {
		MascotaResponseDTO mascota = mascotaService.crear(new MascotaRequestDTO(
				"Paciente" + System.nanoTime(), MascotaRequestDTO.EspecieRequestDTO.CANINO,
				null, null, null, null, null, null, null, null));
		return mascota.id();
	}

	private TurnoClinicoRequestDTO pedir(LocalDateTime inicio) {
		return pedir(inicio, profesionalId, mascotaId);
	}

	private TurnoClinicoRequestDTO pedir(LocalDateTime inicio, Long profesional, Long mascota) {
		return new TurnoClinicoRequestDTO(inicio, "Consulta de control", mascota, profesional, null);
	}
}