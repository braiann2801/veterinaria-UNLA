package com.veterinaria.veterinaria_backend.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.MascotaDelDiaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaDelDiaResponseDTO.TutorVinculadoResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TutorDelDiaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.TutorDelDiaResponseDTO.MascotaDelDiaTutorDTO;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.ReservaGuarderia;
import com.veterinaria.veterinaria_backend.entity.Tutor;
import com.veterinaria.veterinaria_backend.entity.TutorMascota;
import com.veterinaria.veterinaria_backend.entity.TurnoClinico;
import com.veterinaria.veterinaria_backend.mapper.EntityMapper;
import com.veterinaria.veterinaria_backend.repository.MascotaRepository;
import com.veterinaria.veterinaria_backend.repository.ReservaGuarderiaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorMascotaRepository;
import com.veterinaria.veterinaria_backend.repository.TurnoClinicoRepository;
import com.veterinaria.veterinaria_backend.service.JornadaDelDiaService;

/**
 * Implementacion de la consulta de jornada.
 *
 * <p>Une las dos fuentes de presencia (turno clinico y reserva de guarderia) en
 * una sola lista de mascotas, y sobre esa lista arma los dos recortes que la UI
 * necesita: las mascotas, y los tutores agrupados.</p>
 *
 * <p>El recorte de estados se hace en el repositorio y no comparando en memoria:
 * el turno CANCELADO y la reserva CANCELADA o FINALIZADA no ocupan consultorio ni
 * plaza, asi que el mostrador no deberia verlos en la lista del dia. Un turno
 * CANCELADO en la caja haria creer que el cliente vino, y se pagaria una consulta
 * que no se atendio.</p>
 */
@Service
@Transactional(readOnly = true)
public class JornadaDelDiaServiceImpl implements JornadaDelDiaService {

	/**
	 * Estados de reserva que hacen que la mascota este en el local ese dia.
	 *
	 * <p>Es el mismo criterio que usa el aforo (regla 2.1): FINALIZADO y CANCELADO
	 * liberan el cupo y no dejan al animal en la guarderia.</p>
	 */
	private static final List<ReservaGuarderia.EstadoReserva> RESERVAS_QUE_OCCUPAN = List.of(
			ReservaGuarderia.EstadoReserva.RESERVADO,
			ReservaGuarderia.EstadoReserva.INGRESADO);

	private final TurnoClinicoRepository turnoRepository;
	private final ReservaGuarderiaRepository reservaRepository;
	private final MascotaRepository mascotaRepository;
	private final TutorMascotaRepository vinculoRepository;

	public JornadaDelDiaServiceImpl(TurnoClinicoRepository turnoRepository,
			ReservaGuarderiaRepository reservaRepository,
			MascotaRepository mascotaRepository,
			TutorMascotaRepository vinculoRepository) {
		this.turnoRepository = turnoRepository;
		this.reservaRepository = reservaRepository;
		this.mascotaRepository = mascotaRepository;
		this.vinculoRepository = vinculoRepository;
	}

	@Override
	public List<MascotaDelDiaResponseDTO> mascotasDelDia(LocalDate fecha) {
		LocalDate dia = normalizar(fecha);

		// LinkedHashMap: el orden de llegada se usa como desempate, asi que el
		// mapa tiene que conservar el orden de insercion y no uno de hash.
		Map<Long, Acumulado> porMascota = new LinkedHashMap<>();
		acumularTurnos(dia, porMascota);
		acumularReservas(dia, porMascota);

		List<Acumulado> ordenados = new ArrayList<>(porMascota.values());
		// El orden de la jornada lo decide el backend y no la vista: primero el
		// horario mas temprano y al final las mascotas sin hora cargada. Es el mismo
		// criterio de findByFechaOrdenadasPorHorario, y esta vez cubre tambien a las
		// que solo vinieron a un turno.
		ordenados.sort(Comparator
				.comparing(Acumulado::sinHora)
				.thenComparing(Acumulado::primerHorario, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(Acumulado::mascotaId));

		List<MascotaDelDiaResponseDTO> resultado = new ArrayList<>(ordenados.size());
		for (Acumulado acumulado : ordenados) {
			Mascota mascota = resolverMascota(acumulado);
			if (mascota == null) {
				// Turno o reserva de una mascota que ya no existe: se omite la fila en
				// vez de devolver un id sin nombre, que el mostrador no podria leer.
				continue;
			}
			resultado.add(new MascotaDelDiaResponseDTO(
					mascota.getId(),
					mascota.getNombre(),
					mascota.getEspecie() != null ? mascota.getEspecie().name() : null,
					EntityMapper.conductaToApi(mascota.getConducta()),
					List.copyOf(acumulado.motivos),
					acumulado.cantidadTurnos,
					acumulado.estadoGuarderia != null ? acumulado.estadoGuarderia.name() : null,
					acumulado.horaIngreso,
					acumulado.horaSalida,
					tutoresDe(mascota.getId()),
					null,
					null));
		}

		return List.copyOf(resultado);
	}

	@Override
	public List<TutorDelDiaResponseDTO> tutoresDelDia(LocalDate fecha) {
		// Se arma sobre la lista ya resuelta y no con una segunda consulta de la
		// jornada: si el agrupado leyera por su cuenta, el selector de tutores
		// podria ofrecer un nombre que la lista de mascotas no muestra.
		Map<Long, List<MascotaDelDiaTutorDTO>> mascotasPorTutor = new LinkedHashMap<>();
		Map<Long, TutorVinculadoResponseDTO> datosPorTutor = new LinkedHashMap<>();

		for (MascotaDelDiaResponseDTO mascota : mascotasDelDia(fecha)) {
			for (TutorVinculadoResponseDTO tutor : mascota.tutores()) {
				datosPorTutor.putIfAbsent(tutor.tutorId(), tutor);
				// putIfAbsent y no computeIfAbsent con lista mutable: el mismo tutor
				// puede venir por varias mascotas y solo debe aparecer una vez.
				mascotasPorTutor
						.computeIfAbsent(tutor.tutorId(), id -> new ArrayList<>())
						.add(new MascotaDelDiaTutorDTO(
								mascota.mascotaId(), mascota.nombreMascota(), mascota.motivos()));
			}
		}

		List<TutorDelDiaResponseDTO> resultado = new ArrayList<>(datosPorTutor.size());
		for (Map.Entry<Long, TutorVinculadoResponseDTO> entry : datosPorTutor.entrySet()) {
			TutorVinculadoResponseDTO tutor = entry.getValue();
			resultado.add(new TutorDelDiaResponseDTO(
					tutor.tutorId(),
					tutor.nombreCompleto(),
					tutor.dni(),
					List.copyOf(mascotasPorTutor.get(entry.getKey()))));
		}
		return List.copyOf(resultado);
	}

	// ------------------------------------------------------------- fuentes

	private void acumularTurnos(LocalDate dia, Map<Long, Acumulado> porMascota) {
		// Rango semiabierto [inicio, fin): un turno a las 00:00 del dia siguiente
		// no es de hoy. Mismo criterio que usa la agenda para la grilla.
		LocalDateTime desde = dia.atStartOfDay();
		LocalDateTime hasta = dia.plusDays(1).atStartOfDay();

		for (TurnoClinico turno : turnoRepository.findDelDia(desde, hasta)) {
			if (turno.getEstado() == TurnoClinico.EstadoTurno.CANCELADO) {
				// No ocupa consultorio y no genera comanda: no es movimiento del dia.
				continue;
			}
			Mascota mascota = turno.getMascota();
			Acumulado acumulado = porMascota.computeIfAbsent(
					mascota.getId(), id -> new Acumulado(id, mascota));
			acumulado.cantidadTurnos++;
			acumulado.agregarMotivo(MascotaDelDiaResponseDTO.MOTIVO_TURNO);
			acumulado.registrarHorario(turno.getFechaHoraInicio().toLocalTime());
		}
	}

	private void acumularReservas(LocalDate dia, Map<Long, Acumulado> porMascota) {
		for (ReservaGuarderia reserva : reservaRepository.findByFechaAndEstadoIn(dia, RESERVAS_QUE_OCCUPAN)) {
			Mascota mascota = reserva.getMascota();
			Acumulado acumulado = porMascota.computeIfAbsent(
					mascota.getId(), id -> new Acumulado(id, mascota));
			acumulado.agregarMotivo(MascotaDelDiaResponseDTO.MOTIVO_GUARDERIA);
			acumulado.estadoGuarderia = reserva.getEstado();
			acumulado.horaIngreso = reserva.getHoraEntrada();
			acumulado.horaSalida = reserva.getHoraSalida();
			acumulado.registrarHorario(reserva.getHoraEntrada());
		}
	}

	/**
	 * Mascota de la fila.
	 *
	 * <p>Las relaciones son LAZY, asi que la entidad ya viene cargada desde el
	 * turno o la reserva que la trajo. El findById es solo la red de seguridad para
	 * una referencia desasociada.</p>
	 */
	private Mascota resolverMascota(Acumulado acumulado) {
		if (acumulado.mascota != null) {
			return acumulado.mascota;
		}
		return mascotaRepository.findById(acumulado.mascotaId).orElse(null);
	}

	/**
	 * Tutores vinculados a la mascota, con su flag de retiro.
	 *
	 * <p>Ordena por id para que la lista sea estable entre pedidos: el orden de un
	 * {@code IN} en MySQL no esta garantizado, y una lista que se reordena entre dos
	 * recargas hace que el operador elija el renglon equivocado.</p>
	 */
	@Override
	public List<TutorVinculadoResponseDTO> tutoresDe(Long mascotaId) {
		return vinculoRepository.findByMascotaId(mascotaId).stream()
				.sorted(Comparator.comparing(vinculo -> vinculo.getTutor().getId()))
				.map(vinculo -> {
					Tutor tutor = vinculo.getTutor();
					return new TutorVinculadoResponseDTO(
							tutor.getId(),
							tutor.getNombreCompleto(),
							tutor.getDni(),
							vinculo.isAutorizadoRetiro());
				})
				.toList();
	}

	/** La ausencia de fecha es "hoy": el mostrador no pregunta por el dia de hoy. */
	private LocalDate normalizar(LocalDate fecha) {
		return fecha != null ? fecha : LocalDate.now();
	}

	// ------------------------------------------------------------- interno

	/**
	 * Mascota en curso de armado.
	 *
	 * <p>Es mutable a proposito: la jornada se arma en dos pasadas (turnos y
	 * reservas) y recien ahi se resuelve el nombre y los tutores de cada mascota.
	 * Un record inmutable obligaria a reconsultar la mascota por cada fila.</p>
	 */
	private static final class Acumulado {

		private final Long mascotaId;
		private final List<String> motivos = new ArrayList<>(2);
		private int cantidadTurnos;
		private ReservaGuarderia.EstadoReserva estadoGuarderia;
		private LocalTime horaIngreso;
		private LocalTime horaSalida;
		private LocalTime primerHorario;

		/** La mascota se trae desde la entidad que la referencia, para no releerla. */
		private Mascota mascota;

		Acumulado(Long mascotaId, Mascota mascota) {
			this.mascotaId = mascotaId;
			this.mascota = mascota;
		}

		void agregarMotivo(String motivo) {
			if (!motivos.contains(motivo)) {
				motivos.add(motivo);
			}
		}

		/** Conserva el horario mas temprano: el orden del dia lo define el primero. */
		void registrarHorario(LocalTime hora) {
			if (hora == null) {
				return;
			}
			if (primerHorario == null || hora.isBefore(primerHorario)) {
				primerHorario = hora;
			}
		}

		/** Marca las mascotas sin ningun horario cargado, para mandarlas al final. */
		boolean sinHora() {
			return primerHorario == null;
		}

		LocalTime primerHorario() {
			return primerHorario;
		}

		Long mascotaId() {
			return mascotaId;
		}
	}
}
