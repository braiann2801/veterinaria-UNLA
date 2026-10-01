package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.veterinaria.veterinaria_backend.dto.MascotaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MascotaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.ReservaGuarderiaRequestDTO;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.repository.ControlAforoDiarioRepository;
import com.veterinaria.veterinaria_backend.repository.ReservaGuarderiaRepository;
import com.veterinaria.veterinaria_backend.service.MascotaService;
import com.veterinaria.veterinaria_backend.service.ReservaGuarderiaService;

/**
 * TASK-004-HOTFIX: el aforo de 10 no puede depender de que las reservas
 * lleguen de a una.
 *
 * <p><b>Por que este test NO lleva {@code @Transactional}.</b> Con la
 * anotacion, los 15 hilos comparten la transaccion del hilo principal: una
 * sola conexion, un unico contexto de persistencia y ningun commit intermedio.
 * Los bloqueos no se tomarian (el lock es por conexion), todo el trabajo se
 * desharia al final y el test pasaria sin probar nada. Cada hilo necesita su
 * propia transaccion, y eso solo se logra dejando que el proxy de
 * {@code @Transactional} la abra en cada llamada al servicio.</p>
 *
 * <p>Por el mismo motivo la limpieza es manual en {@link #limpiar()}: no hay
 * rollback que deshaga los INSERT.</p>
 */
@SpringBootTest
class AforoConcurrenteTest {

	/** Peticiones simultaneas al mismo dia: 10 entran y 5 rebotan. */
	private static final int PETICIONES = 15;

	private static final LocalDate DIA = LocalDate.of(2027, 3, 15);

	@Autowired
	private ReservaGuarderiaService guarderiaService;

	@Autowired
	private MascotaService mascotaService;

	@Autowired
	private ReservaGuarderiaRepository reservaRepository;

	@Autowired
	private ControlAforoDiarioRepository controlRepository;

	private List<Long> mascotIds;

	@BeforeEach
	void preparar() {
		limpiar();

		mascotIds = new ArrayList<>();
		for (int i = 0; i < PETICIONES; i++) {
			mascotIds.add(crearMascota("Concurrente" + i));
		}
	}

	@AfterEach
	void limpiar() {
		reservaRepository.deleteAllInBatch();
		controlRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("15 reservas simultaneas al mismo dia: exactamente 10 entran y 5 reciben 409")
	void quinceReservasSimultaneasRespetanElAforo() throws Exception {
		Resultado resultado = dispararOleada(DIA, mascotIds);

		assertThat(resultado.inesperados())
				.as("Ninguna reserva debe fallar por una causa distinta al aforo. "
						+ "Primer fallo: " + resultado.detalle())
				.isZero();

		assertThat(resultado.exitos())
				.as("Exactamente 10 reservas deben confirmarse")
				.isEqualTo(10);

		assertThat(resultado.rechazos())
				.as("Las otras 5 deben recibir BusinessConflictException (409)")
				.isEqualTo(5);

		// La verificacion que importa: lo que quedo persistido, no lo que
		// respondieron los hilos.
		assertThat(reservaRepository.contarCuposOcupados(DIA))
				.as("En la base nunca puede haber mas de 10 reservas activas")
				.isEqualTo(10);

		assertThat(reservaRepository.findByFecha(DIA)).hasSize(10);
	}

	@Test
	@DisplayName("Tras la oleada, el semaforo queda alineado con el conteo real")
	void semaforoQuedaAlineadoConLaRealidad() throws Exception {
		dispararOleada(DIA, mascotIds);

		assertThat(controlRepository.findByFecha(DIA))
				.as("La fila de control debe existir para el dia probado")
				.isPresent();

		assertThat(controlRepository.findByFecha(DIA).orElseThrow().getCuposOcupados())
				.as("El contador informativo debe coincidir con la realidad")
				.isEqualTo(10);
	}

	@Test
	@DisplayName("Un dia sin fila previa se inicializa solo y admite sus 10 primeros")
	void diaSinFilaPreviaSeInicializaBajoCarrera() throws Exception {
		LocalDate diaNuevo = DIA.plusDays(1);

		assertThat(controlRepository.findByFecha(diaNuevo))
				.as("Punto de partida: el dia nuevo no tiene semaforo")
				.isEmpty();

		Resultado resultado = dispararOleada(diaNuevo, mascotIds);

		assertThat(resultado.inesperados())
				.as("Primer fallo: " + resultado.detalle())
				.isZero();
		assertThat(resultado.exitos()).isEqualTo(10);
		assertThat(resultado.rechazos()).isEqualTo(5);

		assertThat(guarderiaService.consultarAforo(diaNuevo).cuposOcupados())
				.isEqualTo(10);

		assertThat(controlRepository.findByFecha(diaNuevo))
				.as("La fila de control debe haberse creado sola")
				.isPresent();
	}

	@Test
	@DisplayName("Dos oleadas seguidas sobre el mismo dia no dejan pasar de 10")
	void oleadasSuccessivasNoAcumulanCupos() throws Exception {
		dispararOleada(DIA, mascotIds);

		// Segunda tanda con perros NUEVOS a proposito. Reutilizar los mismos
		// haria que 10 de ellos rebotaran antes por la regla de "esta mascota ya
		// tiene reserva activa" (400), y el test mediria esa regla en vez del
		// aforo. Con otros 15, la unica razon posible para rebotar es que el dia
		// esta lleno.
		List<Long> otrosPerros = new ArrayList<>();
		for (int i = 0; i < PETICIONES; i++) {
			otrosPerros.add(crearMascota("SegundaTanda" + i));
		}

		Resultado segunda = dispararOleada(DIA, otrosPerros);

		assertThat(segunda.exitos())
				.as("El dia ya estaba lleno: no debe entrar ninguna de la segunda tanda")
				.isZero();
		assertThat(segunda.rechazos())
				.as("Las 15 deben rebotar por aforo completo")
				.isEqualTo(PETICIONES);

		assertThat(reservaRepository.contarCuposOcupados(DIA))
				.as("Sigue habiendo 10, no 10 + nada")
				.isEqualTo(10);
	}

	// ------------------------------------------------------------- helpers

	/**
	 * Lanza las reservas de todos los perros a la vez y espera a que terminen.
	 *
	 * <p>El {@link CountDownLatch} de salida hace que todos los hilos estén
	 * esperando y pasen a la vez: sin el,_threads arrancan escalonados mientras
	 * el pool de conexiones los va sirviendo, y la condicion de carrera que se
	 * quiere provocar casi no se presenta.</p>
	 */
	private Resultado dispararOleada(LocalDate fecha, List<Long> mascotaIds) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(PETICIONES);
		CountDownLatch lineaDeSalida = new CountDownLatch(1);

		AtomicInteger exitos = new AtomicInteger();
		AtomicInteger rechazos = new AtomicInteger();
		AtomicInteger inesperados = new AtomicInteger();
		AtomicReference<String> primerFallo = new AtomicReference<>();

		try {
			List<Future<Void>> futuros = new ArrayList<>();

			for (Long mascotaId : mascotaIds) {
				Callable<Void> tarea = () -> {
					lineaDeSalida.await(10, TimeUnit.SECONDS);

					try {
						guarderiaService.crear(pedir(mascotaId, fecha));
						exitos.incrementAndGet();
					} catch (BusinessConflictException rechazada) {
						rechazos.incrementAndGet();
					} catch (Exception otra) {
						// Cualquier otra excepcion es un bug del blindaje, no un
						// rechazo de negocio. Se reporta aparte para que el test
						// falle diciendo cual fue, en vez de "no fueron 10".
						inesperados.incrementAndGet();
						primerFallo.compareAndSet(null, otra.toString());
					}
					return null;
				};
				futuros.add(pool.submit(tarea));
			}

			lineaDeSalida.countDown();

			for (Future<Void> futuro : futuros) {
				futuro.get(60, TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}

		return new Resultado(exitos.get(), rechazos.get(), inesperados.get(), primerFallo.get());
	}

	private long crearMascota(String nombre) {
		MascotaResponseDTO mascota = mascotaService.crear(new MascotaRequestDTO(
				nombre, MascotaRequestDTO.EspecieRequestDTO.CANINO,
				null, null, null, null, null, null, null, null));
		return mascota.id();
	}

	private ReservaGuarderiaRequestDTO pedir(long mascotaId, LocalDate fecha) {
		return new ReservaGuarderiaRequestDTO(fecha,
				ReservaGuarderiaRequestDTO.TipoEstadiaRequestDTO.COMPLETA_24H,
				mascotaId, null, null);
	}

	/**
	 * @param exitos      reservas confirmadas (HTTP 201)
	 * @param rechazos    reservas con 409 por aforo completo
	 * @param inesperados fallos con otra causa: un bug del blindaje
	 * @param detalle     texto del primer fallo inesperado
	 */
	private record Resultado(int exitos, int rechazos, int inesperados, String detalle) {
	}
}