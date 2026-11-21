package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.AgregarStockRequestDTO;
import com.veterinaria.veterinaria_backend.entity.Medicamento;
import com.veterinaria.veterinaria_backend.entity.MovimientoStockMedicamento.TipoMovimiento;
import com.veterinaria.veterinaria_backend.entity.Usuario;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.normalizacion.NormalizadorAr;
import com.veterinaria.veterinaria_backend.repository.MedicamentoRepository;
import com.veterinaria.veterinaria_backend.repository.MovimientoStockMedicamentoRepository;
import com.veterinaria.veterinaria_backend.repository.UsuarioRepository;
import com.veterinaria.veterinaria_backend.service.MedicamentoService;

import tools.jackson.databind.ObjectMapper;

/** ENDPOINTS de catálogo, alta, carga de stock e historial con un token real. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// Contexto propio: cada test hace login y el limitador de IP no se resetea en un
// login exitoso. Compartir el almacen del contexto por defecto agotaba el cupo
// de 20 y hacia fallar UsuarioAbmTest con 429 sin relacion con este modulo.
@TestPropertySource(properties = {
		"veterinaria.auth.login-intentos-maximos=1000",
		"veterinaria.auth.login-ip-intentos-maximos=1000",
		"veterinaria.auth.login-usuario-intentos-maximos=1000"
})
class MedicamentoControllerTest {

	private static final String USUARIO = "farmacia-test";
	private static final String CLAVE = "clave-segura-1";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private MedicamentoRepository medicamentoRepository;
	@Autowired
	private MovimientoStockMedicamentoRepository movimientoRepository;
	@Autowired
	private UsuarioRepository usuarioRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private MedicamentoService medicamentoService;

	private String autorizacion;
	private Long usuarioId;

	@BeforeEach
	void preparar() throws Exception {
		usuarioId = crearUsuario();
		autorizacion = "Bearer " + token();
	}

	private Long crearUsuario() {
		Usuario usuario = new Usuario();
		usuario.setUsername(USUARIO);
		usuario.setNombreCompleto("Farmacia de prueba");
		usuario.setPasswordHash(passwordEncoder.encode(CLAVE));
		usuario.setRol(Usuario.Rol.RECEPCION);
		usuario.setActivo(true);
		usuario.setDebeCambiarPassword(false);
		return usuarioRepository.saveAndFlush(usuario).getId();
	}

	private String token() throws Exception {
		MvcResult respuesta = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("username", USUARIO, "password", CLAVE))))
				.andReturn();
		assertThat(respuesta.getResponse().getStatus()).isEqualTo(200);
		return objectMapper.readTree(respuesta.getResponse().getContentAsString()).get("token").asString();
	}

	private Medicamento crearMedicamento(String nombre, String presentacion, String principio, String laboratorio,
			int stock, int minimo) {
		Medicamento medicamento = new Medicamento();
		medicamento.setNombre(nombre);
		medicamento.setNombreNormalizado(NormalizadorAr.clave(nombre));
		medicamento.setPresentacion(presentacion);
		medicamento.setPresentacionNormalizada(NormalizadorAr.clave(presentacion));
		medicamento.setPrincipioActivo(principio);
		medicamento.setLaboratorio(laboratorio);
		medicamento.setStockActual(stock);
		medicamento.setStockMinimo(minimo);
		medicamento.setActivo(true);
		medicamento.setFechaActualizacion(LocalDateTime.now());
		return medicamentoRepository.saveAndFlush(medicamento);
	}

	private String json(Object cuerpo) throws Exception {
		return objectMapper.writeValueAsString(cuerpo);
	}

	@Test
	@DisplayName("El listado sin filtro trae solo activos, ordenados por nombre y paginados en el servidor")
	void listadoSinFiltro() throws Exception {
		crearMedicamento("Zinc", "Comprimidos", "Zinc", "Lab Z", 4, 0);
		crearMedicamento("Amoxicilina", "Comprimidos", "Amoxicilina", "Lab A", 10, 5);
		Medicamento inactivo = crearMedicamento("Aguardiente", "Frasco", "Etanol", "Lab B", 1, 0);
		inactivo.setActivo(false);
		medicamentoRepository.saveAndFlush(inactivo);
		crearMedicamento("Ibuprofeno", "Comprimidos", "Ibuprofeno", "Lab I", 2, 3);

		mockMvc.perform(get("/api/v1/medicamentos?page=0&size=2")
				.header(HttpHeaders.AUTHORIZATION, autorizacion))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido.length()").value(2))
				.andExpect(jsonPath("$.contenido[0].nombre").value("Amoxicilina"))
				.andExpect(jsonPath("$.contenido[0].bajoStock").value(false))
				.andExpect(jsonPath("$.contenido[1].nombre").value("Ibuprofeno"))
				.andExpect(jsonPath("$.contenido[1].bajoStock").value(true))
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.tamanio").value(2))
				.andExpect(jsonPath("$.totalElementos").value(3))
				.andExpect(jsonPath("$.totalPaginas").value(2));

		mockMvc.perform(get("/api/v1/medicamentos?page=1&size=2")
				.header(HttpHeaders.AUTHORIZATION, autorizacion))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido.length()").value(1))
				.andExpect(jsonPath("$.contenido[0].nombre").value("Zinc"));
	}

	@Test
	@DisplayName("Bajo stock para avisos: solo activos bajo el mínimo, primero los que menos unidades tienen")
	void bajoStockParaAvisos() throws Exception {
		crearMedicamento("Aviso Sin Stock", "Frasco", "A", "Lab", 0, 2);
		crearMedicamento("Aviso Bajo", "Comprimidos", "B", "Lab", 2, 5);
		crearMedicamento("Aviso Normal", "Comprimidos", "C", "Lab", 10, 5);
		crearMedicamento("Aviso Sin Minimo", "Comprimidos", "D", "Lab", 0, 0);
		Medicamento inactivo = crearMedicamento("Aviso Inactivo", "Frasco", "E", "Lab", 0, 3);
		inactivo.setActivo(false);
		medicamentoRepository.saveAndFlush(inactivo);

		mockMvc.perform(get("/api/v1/medicamentos/bajo-stock")
				.header(HttpHeaders.AUTHORIZATION, autorizacion))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].nombre").value("Aviso Sin Stock"))
				.andExpect(jsonPath("$[0].stockActual").value(0))
				.andExpect(jsonPath("$[0].stockMinimo").value(2))
				.andExpect(jsonPath("$[0].bajoStock").value(true))
				.andExpect(jsonPath("$[1].nombre").value("Aviso Bajo"))
				.andExpect(jsonPath("$[1].stockActual").value(2))
				.andExpect(jsonPath("$[1].stockMinimo").value(5));
	}

	@Test
	@DisplayName("La búsqueda es case-insensitive por nombre, principio activo y laboratorio")
	void busquedaPorFiltro() throws Exception {
		crearMedicamento("Amoxicilina", "Comprimidos", "Amoxicilina", "Lab Norte", 10, 0);
		crearMedicamento("Ibuprofeno", "Comprimidos", "Ibuprofeno", "Lab Sur", 10, 0);

		for (String filtro : new String[] { "AMOX", "amoxicilina", "lab norte", "NORTE" }) {
			mockMvc.perform(get("/api/v1/medicamentos").param("q", filtro)
					.header(HttpHeaders.AUTHORIZATION, autorizacion))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.totalElementos").value(1))
					.andExpect(jsonPath("$.contenido[0].nombre").value("Amoxicilina"));
		}

		mockMvc.perform(get("/api/v1/medicamentos").param("q", "principio-inexistente")
				.header(HttpHeaders.AUTHORIZATION, autorizacion))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0))
				.andExpect(jsonPath("$.contenido.length()").value(0));
	}

	@Test
	@DisplayName("El alta con stock inicial registra un INGRESO con el usuario que la hizo")
	void altaConStockInicial() throws Exception {
		MvcResult respuesta = mockMvc.perform(post("/api/v1/medicamentos")
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of(
						"nombre", "Vacuna antirrábica",
						"presentacion", "Frasco 1 dosis",
						"principioActivo", "Virus inactivado",
						"laboratorio", "Lab Vet",
						"stockInicial", 5,
						"stockMinimo", 2,
						"precio", 5000))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.nombre").value("Vacuna antirrábica"))
				.andExpect(jsonPath("$.precio").value(5000.00))
				.andExpect(jsonPath("$.stockActual").value(5))
				.andExpect(jsonPath("$.stockMinimo").value(2))
				.andExpect(jsonPath("$.activo").value(true))
				.andReturn();

		long id = objectMapper.readTree(respuesta.getResponse().getContentAsString()).get("id").asLong();
		assertThat(movimientoRepository.countByMedicamentoIdAndTipo(id, TipoMovimiento.INGRESO)).isEqualTo(1);
		var movimiento = movimientoRepository.findAll().stream()
				.filter(m -> m.getMedicamento().getId().equals(id))
				.findFirst().orElseThrow();
		assertThat(movimiento.getCantidad()).isEqualTo(5);
		assertThat(movimiento.getStockResultante()).isEqualTo(5);
		assertThat(movimiento.getUsuario()).isEqualTo(USUARIO);
		assertThat(movimiento.getObservacion()).isEqualTo("Stock inicial");
	}

	@Test
	@DisplayName("El alta sin stock inicial no registra movimientos")
	void altaSinStockInicial() throws Exception {
		mockMvc.perform(post("/api/v1/medicamentos")
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("nombre", "Suero fisiológico"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.precio").value(0.00))
				.andExpect(jsonPath("$.stockActual").value(0));
		assertThat(movimientoRepository.count()).isZero();
	}

	@Test
	@DisplayName("Un duplicado por nombre y presentación normalizados responde 409 con mensaje claro")
	void altaDuplicada() throws Exception {
		String cuerpo = json(Map.of("nombre", "Amoxicilina 500mg", "presentacion", "Comprimidos"));
		mockMvc.perform(post("/api/v1/medicamentos")
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/medicamentos")
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("nombre", "AMOXICILINA 500 MG", "presentacion", "comprimidos"))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("Conflict"))
				.andExpect(jsonPath("$.mensaje").value(
						org.hamcrest.Matchers.containsString("Ya existe un medicamento")));
	}

	@Test
	@DisplayName("Distinta presentación con el mismo nombre no es duplicado")
	void mismaPresentacionDistintaNoEsDuplicado() throws Exception {
		for (String presentacion : new String[] { "Comprimidos 500mg", "Suspensión 100ml" }) {
			mockMvc.perform(post("/api/v1/medicamentos")
					.header(HttpHeaders.AUTHORIZATION, autorizacion)
					.contentType(MediaType.APPLICATION_JSON)
					.content(json(Map.of("nombre", "Amoxicilina", "presentacion", presentacion))))
					.andExpect(status().isCreated());
		}
		assertThat(medicamentoRepository.count()).isEqualTo(2);
	}

	@Test
	@DisplayName("Un stock inicial negativo responde 400")
	void stockInicialNegativo() throws Exception {
		mockMvc.perform(post("/api/v1/medicamentos")
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("nombre", "Medicamento inválido", "stockInicial", -1))))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Un precio negativo en el alta responde 400")
	void precioNegativo() throws Exception {
		mockMvc.perform(post("/api/v1/medicamentos")
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("nombre", "Precio inválido", "precio", -1))))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("Cualquier empleado puede cambiar el precio; el nuevo valor rige para las comandas nuevas")
	void cambiarPrecio() throws Exception {
		Medicamento medicamento = crearMedicamento("Amoxicilina", "Comprimidos", null, null, 5, 0);

		mockMvc.perform(put("/api/v1/medicamentos/{id}/precio", medicamento.getId())
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("precio", 5000.5))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.precio").value(5000.50))
				.andExpect(jsonPath("$.stockActual").value(5));

		assertThat(medicamentoRepository.findById(medicamento.getId()).orElseThrow().getPrecio())
				.isEqualByComparingTo("5000.50");
	}

	@Test
	@DisplayName("Cambiar el precio con un valor inválido o inexistente responde 400/404")
	void cambiarPrecioInvalido() throws Exception {
		Medicamento medicamento = crearMedicamento("Gasa", null, null, null, 5, 0);

		mockMvc.perform(put("/api/v1/medicamentos/{id}/precio", medicamento.getId())
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/v1/medicamentos/{id}/precio", medicamento.getId())
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("precio", -5))))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/v1/medicamentos/{id}/precio", 99999999)
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("precio", 100))))
				.andExpect(status().isNotFound());
	}

	private Map<String, Object> cuerpoEdicion(String nombre, String presentacion, String principio, String laboratorio,
			double precio, int minimo, int stockActual, int stockAnterior, String observacion) {
		// HashMap y no Map.of: los campos opcionales se envian como null a proposito.
		Map<String, Object> cuerpo = new HashMap<>();
		cuerpo.put("nombre", nombre);
		cuerpo.put("presentacion", presentacion);
		cuerpo.put("principioActivo", principio);
		cuerpo.put("laboratorio", laboratorio);
		cuerpo.put("precio", precio);
		cuerpo.put("stockMinimo", minimo);
		cuerpo.put("stockActual", stockActual);
		cuerpo.put("stockActualAnterior", stockAnterior);
		cuerpo.put("observacionStock", observacion);
		return cuerpo;
	}

	@Test
	@DisplayName("Editar cambia todos los datos del catálogo sin generar movimientos si el stock no se toca")
	void editarDatos() throws Exception {
		Medicamento medicamento = crearMedicamento("Amoxicilina", "Comprimidos", "Amoxicilina", "Lab A", 5, 0);

		mockMvc.perform(put("/api/v1/medicamentos/{id}", medicamento.getId())
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(cuerpoEdicion("AMOXICILINA 500 MG", "Comprimidos 500mg", "Amoxicilina", "Lab B",
						5000.50, 3, 5, 5, ""))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("AMOXICILINA 500 MG"))
				.andExpect(jsonPath("$.presentacion").value("Comprimidos 500mg"))
				.andExpect(jsonPath("$.principioActivo").value("Amoxicilina"))
				.andExpect(jsonPath("$.laboratorio").value("Lab B"))
				.andExpect(jsonPath("$.precio").value(5000.50))
				.andExpect(jsonPath("$.stockMinimo").value(3))
				.andExpect(jsonPath("$.stockActual").value(5));

		Medicamento guardado = medicamentoRepository.findById(medicamento.getId()).orElseThrow();
		assertThat(guardado.getNombreNormalizado()).isEqualTo(NormalizadorAr.clave("AMOXICILINA 500 MG"));
		assertThat(guardado.getPresentacionNormalizada()).isEqualTo(NormalizadorAr.clave("Comprimidos 500mg"));
		assertThat(guardado.getPrecio()).isEqualByComparingTo("5000.50");
		assertThat(movimientoRepository.count()).isZero();
	}

	@Test
	@DisplayName("Editar disminuye el stock y deja un AJUSTE negativo con la observación del operador")
	void editarDisminuyeStock() throws Exception {
		Medicamento medicamento = crearMedicamento("Gasa", "Paquete", null, null, 10, 2);

		mockMvc.perform(put("/api/v1/medicamentos/{id}", medicamento.getId())
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(cuerpoEdicion("Gasa", "Paquete", null, null, 0, 2, 7, 10,
						"Conteo mal cargado: faltaban 3 unidades"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stockActual").value(7));

		assertThat(movimientoRepository.countByMedicamentoIdAndTipo(medicamento.getId(), TipoMovimiento.AJUSTE))
				.isEqualTo(1);
		var movimiento = movimientoRepository.findAll().stream()
				.filter(m -> m.getMedicamento().getId().equals(medicamento.getId()))
				.findFirst().orElseThrow();
		assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.AJUSTE);
		assertThat(movimiento.getCantidad()).isEqualTo(-3);
		assertThat(movimiento.getStockResultante()).isEqualTo(7);
		assertThat(movimiento.getObservacion()).isEqualTo("Conteo mal cargado: faltaban 3 unidades");
		assertThat(movimiento.getUsuario()).isEqualTo(USUARIO);
	}

	@Test
	@DisplayName("Editar aumenta el stock y deja un AJUSTE positivo")
	void editarAumentaStock() throws Exception {
		Medicamento medicamento = crearMedicamento("Suero", "Frasco", null, null, 4, 0);

		mockMvc.perform(put("/api/v1/medicamentos/{id}", medicamento.getId())
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(cuerpoEdicion("Suero", "Frasco", null, null, 0, 0, 6, 4,
						"Corrección de inventario: sobraban 2"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stockActual").value(6));

		var movimiento = movimientoRepository.findAll().stream()
				.filter(m -> m.getMedicamento().getId().equals(medicamento.getId()))
				.findFirst().orElseThrow();
		assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.AJUSTE);
		assertThat(movimiento.getCantidad()).isEqualTo(2);
		assertThat(movimiento.getStockResultante()).isEqualTo(6);
	}

	@Test
	@DisplayName("Cambiar el stock sin observación responde 400 y no altera el inventario")
	void editarStockSinObservacion() throws Exception {
		Medicamento medicamento = crearMedicamento("Jeringa", "Unidad", null, null, 8, 0);

		mockMvc.perform(put("/api/v1/medicamentos/{id}", medicamento.getId())
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(cuerpoEdicion("Jeringa", "Unidad", null, null, 0, 0, 5, 8, ""))))
				.andExpect(status().isBadRequest());

		assertThat(medicamentoRepository.findById(medicamento.getId()).orElseThrow().getStockActual()).isEqualTo(8);
		assertThat(movimientoRepository.count()).isZero();
	}

	@Test
	@DisplayName("Editar con un stock anterior desactualizado responde 409")
	void editarStockObsoleto() throws Exception {
		Medicamento medicamento = crearMedicamento("Venda", "Rollo", null, null, 9, 0);

		mockMvc.perform(put("/api/v1/medicamentos/{id}", medicamento.getId())
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(cuerpoEdicion("Venda", "Rollo", null, null, 0, 0, 6, 12,
						"Otro operador cambió el stock"))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value(
						org.hamcrest.Matchers.containsString("cambió mientras editabas")));

		assertThat(medicamentoRepository.findById(medicamento.getId()).orElseThrow().getStockActual()).isEqualTo(9);
		assertThat(movimientoRepository.count()).isZero();
	}

	@Test
	@DisplayName("Editar hacia un nombre ya usado por otro responde 409; conservar el propio nombre es válido")
	void editarDuplicado() throws Exception {
		Medicamento primero = crearMedicamento("Paracetamol", "Comprimidos", null, null, 5, 0);
		crearMedicamento("Ibuprofeno", "Comprimidos", null, null, 5, 0);

		mockMvc.perform(put("/api/v1/medicamentos/{id}", primero.getId())
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(cuerpoEdicion("Ibuprofeno", "comprimidos", null, null, 0, 0, 5, 5, ""))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value(
						org.hamcrest.Matchers.containsString("Ya existe un medicamento")));

		mockMvc.perform(put("/api/v1/medicamentos/{id}", primero.getId())
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(cuerpoEdicion("PARACETAMOL", "COMPRIMIDOS", null, null, 0, 0, 5, 5, ""))))
				.andExpect(status().isOk());
	}

	@Test
	@DisplayName("Editar con stock negativo responde 400; editar un inexistente responde 404")
	void editarInvalido() throws Exception {
		Medicamento medicamento = crearMedicamento("Alcohol", "Frasco", null, null, 3, 0);

		mockMvc.perform(put("/api/v1/medicamentos/{id}", medicamento.getId())
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(cuerpoEdicion("Alcohol", "Frasco", null, null, 0, 0, -1, 3, "Error de conteo"))))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/medicamentos/{id}", 99999999)
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(cuerpoEdicion("Alcohol", "Frasco", null, null, 0, 0, 3, 3, ""))))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("Cargar stock suma, actualiza la fecha y deja un INGRESO con el stock resultante")
	void cargarStock() throws Exception {
		Medicamento medicamento = crearMedicamento("Ibuprofeno", "Comprimidos", null, null, 2, 5);

		mockMvc.perform(post("/api/v1/medicamentos/{id}/stock", medicamento.getId())
				.header(HttpHeaders.AUTHORIZATION, autorizacion)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("cantidad", 3, "observacion", "Reposición de farmacia"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stockActual").value(5))
				.andExpect(jsonPath("$.bajoStock").value(false));

		assertThat(medicamentoRepository.findById(medicamento.getId()).orElseThrow().getStockActual()).isEqualTo(5);
		assertThat(movimientoRepository.countByMedicamentoIdAndTipo(medicamento.getId(), TipoMovimiento.INGRESO))
				.isEqualTo(1);
		var movimiento = movimientoRepository.findAll().stream()
				.filter(m -> m.getMedicamento().getId().equals(medicamento.getId()))
				.findFirst().orElseThrow();
		assertThat(movimiento.getCantidad()).isEqualTo(3);
		assertThat(movimiento.getStockResultante()).isEqualTo(5);
		assertThat(movimiento.getObservacion()).isEqualTo("Reposición de farmacia");
		assertThat(movimiento.getUsuario()).isEqualTo(USUARIO);
	}

	@Test
	@DisplayName("Cargar cantidad cero o negativa responde 400 y no altera el stock")
	void cargarStockInvalido() throws Exception {
		Medicamento medicamento = crearMedicamento("Paracetamol", "Comprimidos", null, null, 4, 0);

		for (int cantidad : new int[] { 0, -3 }) {
			mockMvc.perform(post("/api/v1/medicamentos/{id}/stock", medicamento.getId())
					.header(HttpHeaders.AUTHORIZATION, autorizacion)
					.contentType(MediaType.APPLICATION_JSON)
					.content(json(Map.of("cantidad", cantidad))))
					.andExpect(status().isBadRequest());
		}
		assertThat(medicamentoRepository.findById(medicamento.getId()).orElseThrow().getStockActual()).isEqualTo(4);
		assertThat(movimientoRepository.count()).isZero();
	}

	@Test
	@DisplayName("El historial es paginado y solo se consulta, sin exponer la entidad")
	void historialMovimientos() throws Exception {
		Medicamento medicamento = crearMedicamento("Suero", "Frasco", null, null, 2, 0);
		medicamentoService.agregarStock(medicamento.getId(), new AgregarStockRequestDTO(2, "Carga 1"), usuarioId);
		medicamentoService.agregarStock(medicamento.getId(), new AgregarStockRequestDTO(3, "Carga 2"), usuarioId);

		mockMvc.perform(get("/api/v1/medicamentos/{id}/movimientos?page=0&size=1", medicamento.getId())
				.header(HttpHeaders.AUTHORIZATION, autorizacion))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contenido.length()").value(1))
				.andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.totalPaginas").value(2))
				.andExpect(jsonPath("$.contenido[0].tipo").value("INGRESO"))
				.andExpect(jsonPath("$.contenido[0].cantidad").value(3))
				.andExpect(jsonPath("$.contenido[0].stockResultante").value(7))
				.andExpect(jsonPath("$.contenido[0].usuario").value(USUARIO))
				.andExpect(jsonPath("$.contenido[0].medicamento").doesNotExist());
	}

	@Test
	@DisplayName("registrarConsumo descuenta y deja un EGRESO_FICHA con la referencia de la ficha")
	void registrarConsumo() {
		Medicamento medicamento = crearMedicamento("Anestesia", "Frasco", null, null, 10, 3);

		medicamentoService.registrarConsumo(medicamento.getId(), 4, 77L, usuarioId, "Consulta de control");

		assertThat(medicamentoRepository.findById(medicamento.getId()).orElseThrow().getStockActual()).isEqualTo(6);
		var movimiento = movimientoRepository.findAll().stream()
				.filter(m -> m.getMedicamento().getId().equals(medicamento.getId()))
				.findFirst().orElseThrow();
		assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.EGRESO_FICHA);
		assertThat(movimiento.getCantidad()).isEqualTo(4);
		assertThat(movimiento.getStockResultante()).isEqualTo(6);
		assertThat(movimiento.getReferenciaId()).isEqualTo(77L);
		assertThat(movimiento.getUsuario()).isEqualTo(USUARIO);
	}

	@Test
	@DisplayName("registrarConsumo sin stock suficiente lanza conflicto y no mueve el inventario")
	void registrarConsumoInsuficiente() {
		Medicamento medicamento = crearMedicamento("Antibiótico", "Frasco", null, null, 2, 1);

		assertThatThrownBy(() -> medicamentoService.registrarConsumo(medicamento.getId(), 3, 12L, usuarioId, "Urgencia"))
				.isInstanceOf(BusinessConflictException.class)
				.hasMessageContaining("Stock insuficiente");

		assertThat(medicamentoRepository.findById(medicamento.getId()).orElseThrow().getStockActual()).isEqualTo(2);
		assertThat(movimientoRepository.count()).isZero();
	}

	@Test
	@DisplayName("Obtener y consultar movimientos de un medicamento inexistente responde 404")
	void inexistente() throws Exception {
		mockMvc.perform(get("/api/v1/medicamentos/999999").header(HttpHeaders.AUTHORIZATION, autorizacion))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/medicamentos/999999/movimientos")
				.header(HttpHeaders.AUTHORIZATION, autorizacion))
				.andExpect(status().isNotFound());
	}
}
