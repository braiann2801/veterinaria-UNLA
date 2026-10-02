package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.MascotaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.ProfesionalRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoClinicoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TurnoClinicoResponseDTO;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.entity.Profesional;
import com.veterinaria.veterinaria_backend.repository.ProfesionalRepository;
import com.veterinaria.veterinaria_backend.service.MascotaService;
import com.veterinaria.veterinaria_backend.service.TurnoClinicoService;

/**
 * TASK-008: turnos clinicos retroactivos y filtrado por fecha.
 *
 * <p>El diagnostico de la fase planteaba que el backend rechazaba las fechas
 * pasadas. Estos tests existen para dejar constancia de que no es asi y para
 * que no vuelva a ser: si alguien agrega un {@code @Future} o una comparacion
 * contra {@code LocalDate.now()} al path de turnos, esta clase se pone roja.</p>
 *
 * <p>Se usan fechas de 2026, que para la maquina de pruebas ya son pasado. Si
 * estos tests usaran {@code LocalDate.now().minusDays(1)} pasarian igual, pero
 * dejarian de verificar nada util si el sistema de reloj estuviera corrido.</p>
 */
@SpringBootTest
@Transactional
class TurnoRetroactivoTest {

	private static final LocalDate AYER = LocalDate.of(2026, 6, 15);

	@Autowired
	private TurnoClinicoService turnoService;

	@Autowired
	private MascotaService mascotaService;

	@Autowired
	private ProfesionalRepository profesionalRepository;

	@Test
	@DisplayName("Se puede agendar un turno en una fecha ya pasada")
	void aceptaTurnoEnFechaPasada() {
		LocalDateTime ayerPorLaManana = AYER.atTime(9, 0);

		TurnoClinicoResponseDTO turno = turnoService.crear(pedir(ayerPorLaManana));

		assertThat(turno.id()).isNotNull();
		assertThat(turno.fechaHoraInicio()).isEqualTo(ayerPorLaManana);
		// La ventana de 45+15 se deriva igual que en un turno futuro.
		assertThat(turno.fechaHoraFin()).isEqualTo(ayerPorLaManana.plusMinutes(45));
		assertThat(turno.fechaFinBloque()).isEqualTo(ayerPorLaManana.plusMinutes(60));
	}

	@Test
	@DisplayName("Un turno muy pasado sigue entrando; la regla es de ocupacion, no de calendario")
	void aceptaTurnoLejanoEnElPasado() {
		LocalDateTime haceCincoAnios = LocalDateTime.of(2021, 3, 1, 10, 0);

		assertThat(turnoService.crear(pedir(haceCincoAnios)).id()).isNotNull();
	}

	@Test
	@DisplayName("La retroactividad no esquiva la ventana de desinfeccion")
	void elPasadoTambienRespetaLosSolapamientos() {
		LocalDateTime ayer = AYER.atTime(9, 0);
		turnoService.crear(pedir(ayer));

		// El minuto 45 sigue bloqueado aunque el dia sea pasado: la regla 2.2 de
		// AGENTS.md es sobre ocupacion de consultorio, no sobre la fecha.
		assertThatThrownBy(() -> turnoService.crear(pedir(ayer.plusMinutes(45))))
				.isInstanceOf(BusinessConflictException.class)
				.hasMessageContaining("desinfeccion");
	}

	@Test
	@DisplayName("GET por fecha devuelve solo los turnos del dia consultado")
	void elListadoRespetaLaFecha() {
		TurnoClinicoResponseDTO ayer = turnoService.crear(pedir(AYER.atTime(9, 0)));
		TurnoClinicoResponseDTO hoy = turnoService.crear(pedir(LocalDate.now().atTime(9, 0)));

		var delAyer = turnoService.listarPorFecha(AYER);

		assertThat(delAyer).extracting(TurnoClinicoResponseDTO::id).contains(ayer.id());
		assertThat(delAyer).extracting(TurnoClinicoResponseDTO::id).doesNotContain(hoy.id());
	}

	@Test
	@DisplayName("Un dia sin turnos devuelve lista vacia, no un 500")
	void unDiaSinTurnosDevuelveVacio() {
		assertThat(turnoService.listarPorFecha(LocalDate.of(2019, 1, 7))).isEmpty();
	}

	@Test
	@DisplayName("El listado del dia sale ordenado por hora, para que la grilla no mezcle consultingorios")
	void elListadoVieneOrdenadoPorHora() {
		// Se crean en orden inverso al cronologico.
		turnoService.crear(pedir(AYER.atTime(16, 0)));
		turnoService.crear(pedir(AYER.atTime(9, 0)));
		turnoService.crear(pedir(AYER.atTime(11, 0)));

		assertThat(turnoService.listarPorFecha(AYER).stream()
				.map(t -> t.fechaHoraInicio().toLocalTime().toString()))
				.containsExactly("09:00", "11:00", "16:00");
	}

	@Test
	@DisplayName("Sin parametro fecha el backend responde por el dia de hoy")
	void sinFechaRespondePorHoy() {
		turnoService.crear(pedir(LocalDate.now().atTime(9, 0)));

		TurnoClinicoResponseDTO manana = turnoService.crear(pedir(LocalDate.now().atTime(11, 0)));

		assertThat(turnoService.listarPorFecha(null))
				.extracting(TurnoClinicoResponseDTO::id)
				.contains(manana.id());
	}

	// ------------------------------------------------------------- helpers

	private TurnoClinicoRequestDTO pedir(LocalDateTime inicio) {
		return new TurnoClinicoRequestDTO(inicio, "Consulta de control",
				crearMascota(), profesionalUnico(), null);
	}

	/**
	 * Un solo profesional por test: varios turnos del mismo profesional a horas
	 * distintas no chocan entre si, y asi cada test queda aislado del orden en
	 * que JUnit los mezcle.
	 */
	private Long profesionalUnico() {
		Long cacheado = cacheProfesional;
		if (cacheado != null) {
			return cacheado;
		}
		ProfesionalRequestDTO request = new ProfesionalRequestDTO(
				"Laura", "Sosa", null, "MAT-" + System.nanoTime(), null);
		Profesional profesional = new Profesional();
		profesional.setNombre(request.nombre());
		profesional.setApellido(request.apellido());
		profesional.setMatricula(request.matricula());
		profesional.setActivo(true);
		cacheProfesional = profesionalRepository.save(profesional).getId();
		return cacheProfesional;
	}

	private Long cacheProfesional;

	/**
	 * Una mascota distinta por turno. No hace falta para las reglas que se
	 * prueban, pero evita que un cambio futuro en la validacion de turnos por
	 * paciente rompa estos tests por un motivo que no es el que persiguen.
	 */
	private Long crearMascota() {
		MascotaResponseDTO mascota = mascotaService.crear(new MascotaRequestDTO(
				"Paciente" + System.nanoTime(), MascotaRequestDTO.EspecieRequestDTO.CANINO,
				null, null, null, null, null, null, null, null));
		return mascota.id();
	}
}