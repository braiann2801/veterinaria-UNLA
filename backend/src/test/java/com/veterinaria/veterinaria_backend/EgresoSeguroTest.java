package com.veterinaria.veterinaria_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Spring Boot 4 movio el autoconfigure de webmvc a este paquete.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// Spring Boot 4.1 usa Jackson 3 (tools.jackson).
import tools.jackson.databind.ObjectMapper;

/**
 * TASK-006: circuito de egreso seguro (reglas 2.4 y 2.5 de AGENTS.md).
 *
 * <p>Este endpoint es el ultimo filtro antes de que una mascota cruce la puerta
 * con un tercero. El costo de cada fallo es distinto y nonequivalente:</p>
 *
 * <ul>
 *   <li>Dejar pasar a un no autorizado = fail de seguridad.</li>
 *   <li>Dejar pasar a un deudor = plata que no vuelve.</li>
 *   <li>Bloquear a un cliente al dia = el cliente se va y no vuelve.</li>
 * </ul>
 *
 * <p>Por eso los tests cubren tambien los casos en que el sistema tiene que
 * <b>dar el paso</b>, no solo bloquear. Un circuito de egreso que solo sabe decir
 * "no" se puede probar con dos asserts; uno que hay que usar todos los dias
 * necesita pruebas de que deja trabajar.</p>
 *
 * <p>La precedencia de los motivos tambien es una prueba: si no autorizado y
 * con deuda devolvieran solo un motivo, el recepcionista arreglaria el primero,
 * el sistema seguiria bloqueando, y el caso volveria al mostrador dos veces.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EgresoSeguroTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	// ------------------------------------------------------ retiro no autorizado

	@Test
	@DisplayName("Un tutor no vinculado a la mascota no puede retirarla")
	void tutorSinVinculoNoRetira() throws Exception {
		long mascotaId = crearMascota("Firulais", "normal");
		long tutorId = crearTutor("35000001");
		long otroTutorId = crearTutor("35000002");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				otroTutorId, mascotaId)).andExpect(status().isCreated());

		mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(false))
				.andExpect(jsonPath("$.motivoBloqueo").value("TUTOR_NO_AUTORIZADO"))
				.andExpect(jsonPath("$.tutorAutorizado").value(false))
				// Un tercero sin vinculo no es "deudor": su deuda es cero y no
				// debe aparecer en el veredicto de una mascota que no es suya.
				.andExpect(jsonPath("$.tieneDeuda").value(false))
				.andExpect(jsonPath("$.saldoDeuda").value(0.00));
	}

	@Test
	@DisplayName("Un cotutor vinculado pero sin autorizadoRetiro no puede retirar")
	void cotutorNoRetira() throws Exception {
		long mascotaId = crearMascota("Toto", "normal");
		long tutorId = crearTutor("35000003");
		// Vinculado como cotutor: existe el vinculo, pero el flag es false (regla 2.4).
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=false",
				tutorId, mascotaId)).andExpect(status().isCreated());

		mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(false))
				.andExpect(jsonPath("$.motivoBloqueo").value("TUTOR_NO_AUTORIZADO"));
	}

	// ------------------------------------------------------------- morosidad

	@Test
	@DisplayName("Un tutor con saldo pendiente no puede retirar y ve su deuda")
	void tutorMorosoNoRetira() throws Exception {
		long mascotaId = crearMascota("Chispa", "normal");
		long tutorId = crearTutor("35000004");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId)).andExpect(status().isCreated());
		emitirComanda(tutorId, "GUARDERIA", 10000);

		mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(false))
				.andExpect(jsonPath("$.motivoBloqueo").value("DEUDA_PENDIENTE"))
				// Autorizado sigue en true: la identidad no es el problema aqui,
				// y el mostrador necesita distinguir "no es de el" de "debe".
				.andExpect(jsonPath("$.tutorAutorizado").value(true))
				.andExpect(jsonPath("$.tieneDeuda").value(true))
				.andExpect(jsonPath("$.saldoDeuda").value(10000.00))
				.andExpect(jsonPath("$.etiquetaMotivo").value("Tutor con deuda pendiente"));
	}

	@Test
	@DisplayName("La deuda de una comanda pagada no bloquea el egreso")
	void comandaPagadaNoBloquea() throws Exception {
		long mascotaId = crearMascota("Roco", "normal");
		long tutorId = crearTutor("35000005");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId)).andExpect(status().isCreated());

		long comandaId = emitirComanda(tutorId, "GUARDERIA", 8000);
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pagos\":[{\"metodoPago\":\"EFECTIVO\",\"monto\":8000}]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADA"));

		mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(true))
				.andExpect(jsonPath("$.motivoBloqueo").value("NINGUNO"))
				.andExpect(jsonPath("$.tieneDeuda").value(false))
				.andExpect(jsonPath("$.saldoDeuda").value(0.00));
	}

	@Test
	@DisplayName("Una comanda parcial bloquea por el saldo, no por el monto total")
	void comandaParcialBloqueaPorElSaldo() throws Exception {
		long mascotaId = crearMascota("Pepita", "normal");
		long tutorId = crearTutor("35000006");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId)).andExpect(status().isCreated());

		long comandaId = emitirComanda(tutorId, "GUARDERIA", 10000);
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pagos\":[{\"metodoPago\":\"EFECTIVO\",\"monto\":4000}]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PARCIAL"));

		mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(false))
				.andExpect(jsonPath("$.motivoBloqueo").value("DEUDA_PENDIENTE"))
				// 10.000 era el precio; lo que se debe son los 6.000 que faltan.
				.andExpect(jsonPath("$.saldoDeuda").value(6000.00));
	}

	@Test
	@DisplayName("Sin autorizacion y con deuda manda TUTOR_NO_AUTORIZADO, pero la deuda se ve")
	void laPrecedenciaEsIdentidadAntesQueDinero() throws Exception {
		long mascotaId = crearMascota("Bolt", "normal");
		long tutorId = crearTutor("35000007");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=false",
				tutorId, mascotaId)).andExpect(status().isCreated());
		emitirComanda(tutorId, "OTRO", 5000);

		mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(false))
				.andExpect(jsonPath("$.motivoBloqueo").value("TUTOR_NO_AUTORIZADO"))
				// Cada regla se evalua siempre: si solo se reportara el primer
				// bloqueo, el mostrador no sabria que hay que cobrar tambien.
				.andExpect(jsonPath("$.tieneDeuda").value(true))
				.andExpect(jsonPath("$.saldoDeuda").value(5000.00));
	}

	@Test
	@DisplayName("La deuda de una mascota ajena no bloquea a este tutor")
	void laDeudaNoSeMezclaEntreTutores() throws Exception {
		long mascotaId = crearMascota("Nube", "normal");
		long alDia = crearTutor("35000008");
		long deudor = crearTutor("35000009");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				alDia, mascotaId)).andExpect(status().isCreated());

		emitirComanda(deudor, "GUARDERIA", 20000);

		// Sin esto, cualquier tutor nuevo quedaria bloqueado por deudas ajenas
		// y el mostrador aprenderia a ignorar el semaforo rojo.
		mockMvc.perform(validar(mascotaId, alDia))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(true))
				.andExpect(jsonPath("$.tieneDeuda").value(false));
	}

	// ---------------------------------------------------------- conducta

	@Test
	@DisplayName("Una mascota que grune avisa pero NO bloquea el retiro")
	void alertaDeConductaNoBloquea() throws Exception {
		long mascotaId = crearMascota("Max", "gruñe");
		long tutorId = crearTutor("35000010");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId)).andExpect(status().isCreated());

		mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(status().isOk())
				// El animal se entrega igual: la conducta es informacion para el
				// que recibe, no un motivo para retener a una mascota ya atendida.
				.andExpect(jsonPath("$.puedeRetirar").value(true))
				.andExpect(jsonPath("$.motivoBloqueo").value("NINGUNO"))
				.andExpect(jsonPath("$.alertaConducta").value(true))
				.andExpect(jsonPath("$.conducta").value("gruñe"))
				.andExpect(jsonPath("$.conductaUltimaConsulta").doesNotExist())
				.andExpect(jsonPath("$.mensaje",
						org.hamcrest.Matchers.containsString("precaución")));
	}

	@Test
	@DisplayName("La ficha clinica REACTIVO enciende la alerta de egreso")
	void fichaReactivaEnciendeAlerta() throws Exception {
		long mascotaId = crearMascota("Sira", "normal");
		long tutorId = crearTutor("35000011");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId)).andExpect(status().isCreated());
		long comandaId = registrarConsulta(mascotaId, crearProfesional("MAT-E1"),
				"REACTIVO", "2026-03-10T10:00:00");
		cobrarTodo(comandaId);

		mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(true))
				.andExpect(jsonPath("$.alertaConducta").value(true))
				.andExpect(jsonPath("$.conducta").value("normal"))
				.andExpect(jsonPath("$.conductaUltimaConsulta").value("REACTIVO"))
				.andExpect(jsonPath("$.fechaUltimaConsulta").value("2026-03-10T10:00:00"));
	}

	@Test
	@DisplayName("Solo cuenta la ficha mas reciente: una vieja REACTIVO ya no asusta")
	void soloCuentaLaFichaMasReciente() throws Exception {
		long mascotaId = crearMascota("Luna", "normal");
		long tutorId = crearTutor("35000012");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId)).andExpect(status().isCreated());
		long profesionalId = crearProfesional("MAT-E2");

		// Veterinario que lo toco hace un ano y otro que hoy lovio tranquilo.
		cobrarTodo(registrarConsulta(mascotaId, profesionalId, "AGRESIVO", "2025-03-10T10:00:00"));
		cobrarTodo(registrarConsulta(mascotaId, profesionalId, "TRANQUILO", "2026-03-10T10:00:00"));

		mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(true))
				// Si se acumulara la alerta historica, el perro quedaria marcado
				// para siempre y la alerta dejaria de significar nada.
				.andExpect(jsonPath("$.alertaConducta").value(false))
				.andExpect(jsonPath("$.conductaUltimaConsulta").value("TRANQUILO"));
	}

	@Test
	@DisplayName("Una ficha vieja con alerta sigue contando si es la ultima")
	void laUltimaFichaAlertaVenceALaTranquila() throws Exception {
		long mascotaId = crearMascota("Kira", "normal");
		long tutorId = crearTutor("35000013");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId)).andExpect(status().isCreated());
		long profesionalId = crearProfesional("MAT-E3");

		cobrarTodo(registrarConsulta(mascotaId, profesionalId, "TRANQUILO", "2026-03-01T10:00:00"));
		cobrarTodo(registrarConsulta(mascotaId, profesionalId, "AGRESIVO", "2026-03-12T10:00:00"));

		mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alertaConducta").value(true))
				.andExpect(jsonPath("$.conductaUltimaConsulta").value("AGRESIVO"))
				.andExpect(jsonPath("$.puedeRetirar").value(true));
	}

	@Test
	@DisplayName("La ficha recien cargada bloquea el egreso hasta que el mostrador la cobra")
	void laFichaRecienteGeneraDeudaQueBloqueaElEgreso() throws Exception {
		long mascotaId = crearMascota("Toby", "normal");
		long tutorId = crearTutor("35000018");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId)).andExpect(status().isCreated());

		// Nada de esto es un efecto secundario raro: es el desacople de la
		// regla 2.3. El veterinario no cobra, pero su ficha deja una comanda
		// impaga, y una comanda impaga es exactamente lo que este semaforo
		// tiene que ver. Sin este test, un dia se "arregla" el egreso para que
		// ignore las comandas de la consulta y nadie se da cuenta de que el
		// consultorio dejo de facturar.
		long comandaId = registrarConsulta(mascotaId, crearProfesional("MAT-E4"),
				"TRANQUILO", "2026-03-14T10:00:00");

		mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(false))
				.andExpect(jsonPath("$.motivoBloqueo").value("DEUDA_PENDIENTE"))
				.andExpect(jsonPath("$.saldoDeuda").value(8000.00));

		cobrarTodo(comandaId);

		mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(true))
				.andExpect(jsonPath("$.motivoBloqueo").value("NINGUNO"));
	}

	// ------------------------------------------------------------ egreso limpio

	@Test
	@DisplayName("Egreso limpio: autorizado, al dia y sin alertas")
	void egresoLimpio() throws Exception {
		long mascotaId = crearMascota("Otto", "normal");
		long tutorId = crearTutor("35000014");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId)).andExpect(status().isCreated());

		mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(true))
				.andExpect(jsonPath("$.motivoBloqueo").value("NINGUNO"))
				.andExpect(jsonPath("$.tutorAutorizado").value(true))
				.andExpect(jsonPath("$.tieneDeuda").value(false))
				.andExpect(jsonPath("$.saldoDeuda").value(0.00))
				.andExpect(jsonPath("$.alertaConducta").value(false))
				.andExpect(jsonPath("$.conducta").value("normal"))
				.andExpect(jsonPath("$.mascotaId").value(mascotaId))
				.andExpect(jsonPath("$.tutorId").value(tutorId))
				.andExpect(jsonPath("$.etiquetaMotivo").value("Retiro permitido"));
	}

	@Test
	@DisplayName("El semaforo coincide con la morosidad del endpoint de caja")
	void elEgresoNoContradiceLaMorosidad() throws Exception {
		long mascotaId = crearMascota("Wally", "normal");
		long tutorId = crearTutor("35000015");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId)).andExpect(status().isCreated());
		long comandaId = emitirComanda(tutorId, "GUARDERIA", 12000);
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pagos\":[{\"metodoPago\":\"EFECTIVO\",\"monto\":5000}]}"))
				.andExpect(status().isOk());

		String egreso = mockMvc.perform(validar(mascotaId, tutorId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String caja = mockMvc.perform(get("/api/v1/tutores/{id}/deuda", tutorId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		// Dos sumas de deuda en dos lugares son dos verdades distintas apenas
		// una de las dos se toca. El egreso delega en caja, pero el test lo
		// verifica para que una futura "optimizacion" no rompa la equivalencia.
		assertEquals(new BigDecimal(objectMapper.readTree(egreso).get("saldoDeuda").asText()),
				new BigDecimal(objectMapper.readTree(caja).get("totalDeuda").asText()));
	}

	// ------------------------------------------------------------- errores

	@Test
	@DisplayName("Un DNI desconocido responde 404, no un semaforo verde")
	void dniInexistenteDevuelve404() throws Exception {
		long mascotaId = crearMascota("Pipa", "normal");

		// Un 200 con puedeRetirar=false seria "no autorizado" y el mostrador
		// diria que la persona no esta habilitada, cuando en realidad nunca
		// estuvo cargada. Son dos problemas distintos para el cliente.
		mockMvc.perform(get("/api/v1/egreso/validar")
						.param("mascotaId", String.valueOf(mascotaId))
						.param("tutorDni", "39999999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.mensaje",
						org.hamcrest.Matchers.containsString("No existe un tutor")));
	}

	@Test
	@DisplayName("Una mascota inexistente responde 404")
	void mascotaInexistenteDevuelve404() throws Exception {
		long tutorId = crearTutor("35000016");

		mockMvc.perform(get("/api/v1/egreso/validar")
						.param("mascotaId", "987654321")
						.param("tutorDni", dniDe(tutorId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.mensaje",
						org.hamcrest.Matchers.containsString("No existe una mascota")));
	}

	@Test
	@DisplayName("Sin DNI no hay consulta posible: 400 explicito")
	void sinDniDevuelve400() throws Exception {
		long mascotaId = crearMascota("Felis", "normal");

		mockMvc.perform(get("/api/v1/egreso/validar")
						.param("mascotaId", String.valueOf(mascotaId)))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("El DNI se normaliza: espacios alrededor no cambian el veredicto")
	void elDniToleraEspacios() throws Exception {
		long mascotaId = crearMascota("Uma", "normal");
		long tutorId = crearTutor("35000017");
		mockMvc.perform(post("/api/v1/tutores/{t}/mascotas/{m}?autorizadoRetiro=true",
				tutorId, mascotaId)).andExpect(status().isCreated());

		// Viene de un lector de documentos o de un pegado a mano; pedirle al
		// recepcionista que limpie el campo seria un friccion evitable.
		mockMvc.perform(get("/api/v1/egreso/validar")
						.param("mascotaId", String.valueOf(mascotaId))
						.param("tutorDni", "  35000017  "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.puedeRetirar").value(true));
	}

	// ------------------------------------------------------------- helpers

	private MockHttpServletRequestBuilder validar(long mascotaId, long tutorId) throws Exception {
		return get("/api/v1/egreso/validar")
				.param("mascotaId", String.valueOf(mascotaId))
				.param("tutorDni", dniDe(tutorId));
	}

	private static void assertEquals(BigDecimal esperado, BigDecimal actual) {
		Assertions.assertEquals(esperado.setScale(2), actual.setScale(2),
				"El egreso y la caja no pueden reportar deudas distintas");
	}

	private String dniDe(long tutorId) throws Exception {
		String json = mockMvc.perform(get("/api/v1/tutores/{id}", tutorId))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("dni").asText();
	}

	/**
	 * Registra una ficha clinica y devuelve el id de la comanda que emitio.
	 *
	 * <p>Devolver el id es necesario, no un convenience: la ficha genera una
	 * comanda PENDIENTE y esa comanda es deuda, asi que sin cobrarla el egreso
	 * queda bloqueado por DEUDA_PENDIENTE. Quien testea conducta tiene que
	 * asumir ese estado y no mezclarlo con el motivo que esta mirando.</p>
	 */
	private long registrarConsulta(long mascotaId, long profesionalId,
			String conducta, String fechaAtencion) throws Exception {
		// La comanda sale de la tarifa vigente: sin cargarla el alta se rechaza.
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", "CONSULTA_CLINICA")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"monto\":8000}"))
				.andExpect(status().isOk());

		String json = mockMvc.perform(post("/api/v1/consultas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"mascotaId":%d,"profesionalId":%d,"fechaAtencion":"%s",
								 "anamnesis":"Consulta de control","conductaObservada":"%s"}
								""".formatted(mascotaId, profesionalId, fechaAtencion, conducta)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("comandaId").asLong();
	}

	/** Deja al tutor al dia para que el egreso no se frene por la ficha recien cargada. */
	private void cobrarTodo(long comandaId) throws Exception {
		mockMvc.perform(post("/api/v1/caja/pagos/{id}", comandaId)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"pagos\":[{\"metodoPago\":\"EFECTIVO\",\"monto\":8000}]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PAGADA"));
	}

	private long emitirComanda(long tutorId, String concepto, long monto) throws Exception {
		mockMvc.perform(put("/api/v1/comandas/tarifas/{concepto}", concepto)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"monto\":%d}".formatted(monto)))
				.andExpect(status().isOk());

		String json = mockMvc.perform(post("/api/v1/comandas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"concepto":"%s","tutorId":%d,"descripcion":"Consumo"}
								""".formatted(concepto, tutorId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearMascota(String nombre, String conducta) throws Exception {
		String json = mockMvc.perform(post("/api/v1/mascotas")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"%s","especie":"CANINO","raza":"Mestizo","sexo":"Macho",
								 "conducta":"%s"}
								""".formatted(nombre, conducta)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearTutor(String dni) throws Exception {
		String json = mockMvc.perform(post("/api/v1/tutores")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Rita","apellido":"Sosa","dni":"%s"}
								""".formatted(dni)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private long crearProfesional(String matricula) throws Exception {
		String json = mockMvc.perform(post("/api/v1/profesionales")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"nombre":"Laura","apellido":"Ruiz","matricula":"%s"}
								""".formatted(matricula)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}
}