package com.veterinaria.veterinaria_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.AgregarStockRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MedicamentoActualizacionRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MedicamentoRequestDTO;
import com.veterinaria.veterinaria_backend.dto.MedicamentoResponseDTO;
import com.veterinaria.veterinaria_backend.dto.MovimientoStockResponseDTO;
import com.veterinaria.veterinaria_backend.dto.PaginaResponseDTO;
import com.veterinaria.veterinaria_backend.dto.PrecioMedicamentoRequestDTO;
import com.veterinaria.veterinaria_backend.entity.Medicamento;
import com.veterinaria.veterinaria_backend.entity.MovimientoStockMedicamento;
import com.veterinaria.veterinaria_backend.entity.MovimientoStockMedicamento.TipoMovimiento;
import com.veterinaria.veterinaria_backend.entity.Usuario;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.repository.MedicamentoRepository;
import com.veterinaria.veterinaria_backend.repository.MedicamentoUsadoFichaRepository;
import com.veterinaria.veterinaria_backend.repository.MovimientoStockMedicamentoRepository;
import com.veterinaria.veterinaria_backend.repository.UsuarioRepository;
import com.veterinaria.veterinaria_backend.service.MedicamentoService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MedicamentoServiceImpl implements MedicamentoService {

	private static final int TAMANIO_MAXIMO = 200;
	private static final int FILTRO_MAXIMO = 120;

	private final MedicamentoRepository medicamentoRepository;
	private final MovimientoStockMedicamentoRepository movimientoRepository;
	private final MedicamentoUsadoFichaRepository medicamentoUsadoRepository;
	private final UsuarioRepository usuarioRepository;
	private final Clock reloj;

	@Override
	public PaginaResponseDTO<MedicamentoResponseDTO> listar(String q, boolean incluirInactivos, int page, int size) {
		String filtro = q == null ? "" : q.trim();
		if (filtro.length() > FILTRO_MAXIMO) {
			filtro = filtro.substring(0, FILTRO_MAXIMO);
		}
		PageRequest pagina = PageRequest.of(
				Math.max(page, 0),
				Math.min(Math.max(size, 1), TAMANIO_MAXIMO),
				Sort.by("nombre").ascending().and(Sort.by("id").ascending()));
		Page<Medicamento> encontrados = medicamentoRepository.buscar(filtro, incluirInactivos, pagina);
		Set<Long> enUso = idsEnUso(encontrados.getContent().stream().map(Medicamento::getId).toList());
		return PaginaResponseDTO.desde(encontrados, medicamento -> toResponse(medicamento, enUso.contains(medicamento.getId())));
	}

	@Override
	public long contarInactivos() {
		return medicamentoRepository.countByActivoFalse();
	}

	@Override
	public List<MedicamentoResponseDTO> bajoStock() {
		List<Medicamento> encontrados = medicamentoRepository.bajoStock();
		Set<Long> enUso = idsEnUso(encontrados.stream().map(Medicamento::getId).toList());
		return encontrados.stream()
				.map(medicamento -> toResponse(medicamento, enUso.contains(medicamento.getId())))
				.toList();
	}

	@Override
	public MedicamentoResponseDTO obtener(Long id) {
		Medicamento medicamento = buscar(id);
		return toResponse(medicamento, estaUsado(id));
	}

	@Override
	@Transactional
	public MedicamentoResponseDTO crear(MedicamentoRequestDTO request, Long usuarioId) {
		Optional<Medicamento> existente = medicamentoRepository.findByNombreNormalizadoAndPresentacionNormalizada(
				request.nombreNormalizado(), request.presentacionNormalizada());
		if (existente.isPresent()) {
			String detalle = request.presentacion() == null || request.presentacion().isBlank()
					? ""
					: " (" + request.presentacion() + ")";
			throw new BusinessConflictException("Ya existe un medicamento llamado '"
					+ request.nombre() + "'" + detalle + ".");
		}

		Medicamento medicamento = new Medicamento();
		medicamento.setNombre(request.nombre());
		medicamento.setNombreNormalizado(request.nombreNormalizado());
		medicamento.setPresentacion(request.presentacion());
		medicamento.setPresentacionNormalizada(request.presentacionNormalizada());
		medicamento.setPrincipioActivo(request.principioActivo());
		medicamento.setLaboratorio(request.laboratorio());
		medicamento.setPrecio(request.precio());
		medicamento.setStockActual(request.stockInicial());
		medicamento.setStockMinimo(request.stockMinimo());
		medicamento.setActivo(true);
		medicamento.setFechaActualizacion(LocalDateTime.now(reloj));

		Medicamento guardado = medicamentoRepository.saveAndFlush(medicamento);
		if (guardado.getStockActual() > 0) {
			registrarMovimiento(guardado, TipoMovimiento.INGRESO, guardado.getStockActual(), null,
					"Stock inicial", usuarioId);
		}
		return toResponse(guardado, false);
	}

	@Override
	@Transactional
	public MedicamentoResponseDTO actualizar(Long id, MedicamentoActualizacionRequestDTO request, Long usuarioId) {
		Medicamento medicamento = bloquear(id);

		Optional<Medicamento> duplicado = medicamentoRepository
				.findByNombreNormalizadoAndPresentacionNormalizadaAndIdNot(
						request.nombreNormalizado(), request.presentacionNormalizada(), id);
		if (duplicado.isPresent()) {
			String detalle = request.presentacion() == null || request.presentacion().isBlank()
					? ""
					: " (" + request.presentacion() + ")";
			throw new BusinessConflictException("Ya existe un medicamento llamado '"
					+ request.nombre() + "'" + detalle + ".");
		}

		int stockActual = medicamento.getStockActual();
		int stockDeseado = request.stockActual();
		boolean cambioStock = stockDeseado != stockActual;
		if (cambioStock) {
			if (request.stockActualAnterior() != stockActual) {
				throw new BusinessConflictException("El stock de " + medicamento.getNombre()
						+ " cambió mientras editabas (ahora hay " + stockActual
						+ " unidades). Recargá la pantalla y volvé a intentarlo.");
			}
			if (request.observacionStock() == null || request.observacionStock().isBlank()) {
				throw new BadRequestException("La observación es obligatoria cuando se cambia el stock.");
			}
		}

		medicamento.setNombre(request.nombre());
		medicamento.setNombreNormalizado(request.nombreNormalizado());
		medicamento.setPresentacion(request.presentacion());
		medicamento.setPresentacionNormalizada(request.presentacionNormalizada());
		medicamento.setPrincipioActivo(request.principioActivo());
		medicamento.setLaboratorio(request.laboratorio());
		medicamento.setPrecio(request.precio());
		medicamento.setStockMinimo(request.stockMinimo());
		medicamento.setStockActual(stockDeseado);
		medicamento.setFechaActualizacion(LocalDateTime.now(reloj));

		Medicamento guardado = medicamentoRepository.save(medicamento);
		if (cambioStock) {
			registrarMovimiento(guardado, TipoMovimiento.AJUSTE, stockDeseado - stockActual, null,
					request.observacionStock(), usuarioId);
		}
		return toResponse(guardado, estaUsado(id));
	}

	@Override
	@Transactional
	public MedicamentoResponseDTO agregarStock(Long id, AgregarStockRequestDTO request, Long usuarioId) {
		if (request.cantidad() == null || request.cantidad() <= 0) {
			throw new BadRequestException("La cantidad a agregar debe ser mayor a cero");
		}
		Medicamento medicamento = bloquear(id);
		verificarActivo(medicamento, "cargar stock");
		long resultado = (long) medicamento.getStockActual() + request.cantidad();
		if (resultado > Integer.MAX_VALUE) {
			throw new BusinessConflictException("El stock resultante supera el máximo permitido ("
					+ Integer.MAX_VALUE + " unidades)");
		}
		medicamento.setStockActual((int) resultado);
		medicamento.setFechaActualizacion(LocalDateTime.now(reloj));
		Medicamento guardado = medicamentoRepository.save(medicamento);
		registrarMovimiento(guardado, TipoMovimiento.INGRESO, request.cantidad(), null,
				request.observacion(), usuarioId);
		return toResponse(guardado, estaUsado(id));
	}

	@Override
	@Transactional
	public MedicamentoResponseDTO actualizarPrecio(Long id, PrecioMedicamentoRequestDTO request) {
		Medicamento medicamento = buscar(id);
		BigDecimal precio = request.precio().setScale(2, RoundingMode.HALF_UP);
		medicamento.setPrecio(precio);
		medicamento.setFechaActualizacion(LocalDateTime.now(reloj));
		return toResponse(medicamentoRepository.save(medicamento), estaUsado(id));
	}

	@Override
	@Transactional
	public void eliminar(Long id) {
		Medicamento medicamento = bloquear(id);
		if (estaUsado(id)) {
			throw new BusinessConflictException("El medicamento '" + medicamento.getNombre()
					+ "' fue usado en fichas clínicas: no se puede borrar. Desactivalo con el toggle "
					+ "para que deje de estar disponible sin perder la trazabilidad.");
		}
		// Sin fichas asociadas, sus movimientos solo documentan la carga que se
		// quiere corregir: se van con el medicamento en la misma transacción.
		// Se borra entidad por entidad (no bulk JPQL) para que la persistence
		// context no conserve movimientos apuntando a un medicamento borrado.
		movimientoRepository.deleteByMedicamentoId(id);
		medicamentoRepository.delete(medicamento);
		medicamentoRepository.flush();
	}

	@Override
	@Transactional
	public MedicamentoResponseDTO desactivar(Long id) {
		Medicamento medicamento = bloquear(id);
		if (medicamento.isActivo()) {
			medicamento.setActivo(false);
			medicamento.setFechaActualizacion(LocalDateTime.now(reloj));
			medicamentoRepository.save(medicamento);
		}
		return toResponse(medicamento, estaUsado(id));
	}

	@Override
	@Transactional
	public MedicamentoResponseDTO reactivar(Long id) {
		Medicamento medicamento = bloquear(id);
		Optional<Medicamento> duplicado = medicamentoRepository
				.findByNombreNormalizadoAndPresentacionNormalizadaAndIdNot(
						medicamento.getNombreNormalizado(), medicamento.getPresentacionNormalizada(), id);
		if (duplicado.isPresent()) {
			throw new BusinessConflictException("Ya existe otro medicamento con el nombre '"
					+ medicamento.getNombre() + "' y su presentación: no se puede reactivar este.");
		}
		medicamento.setActivo(true);
		medicamento.setFechaActualizacion(LocalDateTime.now(reloj));
		return toResponse(medicamentoRepository.save(medicamento), estaUsado(id));
	}

	@Override
	public PaginaResponseDTO<MovimientoStockResponseDTO> movimientos(Long id, int page, int size) {
		buscar(id); // 404 antes de devolver una historia vacia de un medicamento inexistente.
		PageRequest pagina = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), TAMANIO_MAXIMO));
		return PaginaResponseDTO.desde(
				movimientoRepository.findByMedicamentoIdOrderByFechaHoraDescIdDesc(id, pagina),
				MedicamentoServiceImpl::toMovimientoResponse);
	}

	@Override
	@Transactional
	public void registrarConsumo(Long medicamentoId, int cantidad, Long referenciaId, Long usuarioId,
			String observacion) {
		if (cantidad <= 0) {
			throw new BadRequestException("La cantidad consumida debe ser mayor a cero");
		}
		Medicamento medicamento = bloquear(medicamentoId);
		verificarActivo(medicamento, "usarlo en una ficha");
		if (medicamento.getStockActual() < cantidad) {
			throw new BusinessConflictException("Stock insuficiente de " + medicamento.getNombre()
					+ ": hay " + medicamento.getStockActual() + " unidades y se necesitan " + cantidad + ".");
		}
		medicamento.setStockActual(medicamento.getStockActual() - cantidad);
		medicamento.setFechaActualizacion(LocalDateTime.now(reloj));
		Medicamento guardado = medicamentoRepository.save(medicamento);
		registrarMovimiento(guardado, TipoMovimiento.EGRESO_FICHA, cantidad, referenciaId, observacion, usuarioId);
	}

	// ------------------------------------------------------------- internos

	private Medicamento buscar(Long id) {
		return medicamentoRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No existe el medicamento " + id));
	}

	/** Lectura con {@code FOR UPDATE}: serializa los movimientos sobre la misma fila. */
	private Medicamento bloquear(Long id) {
		return medicamentoRepository.bloquearPorId(id)
				.orElseThrow(() -> new ResourceNotFoundException("No existe el medicamento " + id));
	}

	private void verificarActivo(Medicamento medicamento, String accion) {
		if (!medicamento.isActivo()) {
			throw new BadRequestException("El medicamento '" + medicamento.getNombre()
					+ "' está desactivado: reactivalo antes de " + accion + ".");
		}
	}

	/**
	 * "Usado" significa consumido en una ficha, no simplemente cargado: un
	 * medicamento cargado por error tiene INGRESO pero se puede borrar.
	 */
	private boolean estaUsado(Long medicamentoId) {
		return movimientoRepository.existsByMedicamentoIdAndTipo(medicamentoId, TipoMovimiento.EGRESO_FICHA)
				|| medicamentoUsadoRepository.existsByMedicamentoId(medicamentoId);
	}

	/** Misma regla que {@link #estaUsado}, resuelta en dos consultas para el listado. */
	private Set<Long> idsEnUso(List<Long> ids) {
		if (ids.isEmpty()) {
			return Set.of();
		}
		Set<Long> enUso = new HashSet<>(movimientoRepository.idsConConsumo(TipoMovimiento.EGRESO_FICHA, ids));
		enUso.addAll(medicamentoUsadoRepository.idsMedicamentosUsados(ids));
		return enUso;
	}

	private void registrarMovimiento(Medicamento medicamento, TipoMovimiento tipo, int cantidad,
			Long referenciaId, String observacion, Long usuarioId) {
		MovimientoStockMedicamento movimiento = new MovimientoStockMedicamento();
		movimiento.setMedicamento(medicamento);
		movimiento.setTipo(tipo);
		movimiento.setCantidad(cantidad);
		movimiento.setStockResultante(medicamento.getStockActual());
		movimiento.setObservacion(observacion);
		movimiento.setReferenciaId(referenciaId);
		movimiento.setUsuario(resolverUsuario(usuarioId));
		movimiento.setFechaHora(LocalDateTime.now(reloj));
		movimientoRepository.save(movimiento);
	}

	/** Snapshot del username: la cuenta puede borrarse, el historial no. */
	private String resolverUsuario(Long usuarioId) {
		if (usuarioId == null) {
			return "sistema";
		}
		return usuarioRepository.findById(usuarioId)
				.map(Usuario::getUsername)
				.orElse("sistema");
	}

	private static MedicamentoResponseDTO toResponse(Medicamento medicamento, boolean enUso) {
		return new MedicamentoResponseDTO(
				medicamento.getId(),
				medicamento.getNombre(),
				medicamento.getPresentacion(),
				medicamento.getPrincipioActivo(),
				medicamento.getLaboratorio(),
				medicamento.precioCobrable(),
				medicamento.getStockActual(),
				medicamento.getStockMinimo(),
				medicamento.getStockMinimo() > 0 && medicamento.getStockActual() < medicamento.getStockMinimo(),
				medicamento.isActivo(),
				enUso,
				medicamento.getFechaActualizacion());
	}

	private static MovimientoStockResponseDTO toMovimientoResponse(MovimientoStockMedicamento movimiento) {
		return new MovimientoStockResponseDTO(
				movimiento.getId(),
				movimiento.getTipo(),
				movimiento.getCantidad(),
				movimiento.getStockResultante(),
				movimiento.getObservacion(),
				movimiento.getReferenciaId(),
				movimiento.getUsuario(),
				movimiento.getFechaHora());
	}
}
