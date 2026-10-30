package com.veterinaria.veterinaria_backend.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.veterinaria.veterinaria_backend.dto.ComandaCobroResponseDTO;
import com.veterinaria.veterinaria_backend.dto.PagoResponseDTO;
import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.entity.Pago;

/**
 * Proyeccion de entidades contables a DTOs.
 *
 * <p>Vive aparte de {@code EntityMapper} porque el mapeo de ficha clinica y el
 * de caja son problemas distintos: el primero no debe tocar campos de cobro y
 * el segundo si. Mezclarlos en una sola clase cruzaria los dos mundos y
 *volveria exactamente el acoplamiento que la regla 2.3 prohibe.</p>
 */
@Component
public class CobroMapper {

	/**
	 * Proyecta la comanda a su vista publica.
	 *
	 * <p>{@code totalPagado} no se pasa: {@code ComandaCobroResponseDTO} lo
	 * deriva de {@code montoTotal - saldoPendiente}.</p>
	 */
	public ComandaCobroResponseDTO toResponse(ComandaCobro comanda) {
		return new ComandaCobroResponseDTO(
				comanda.getId(),
				comanda.getConcepto() != null ? comanda.getConcepto().name() : null,
				comanda.getMontoTotal(),
				comanda.getSaldoPendiente(),
				comanda.getEstado() != null ? comanda.getEstado().name() : null,
				comanda.getTutor() != null ? comanda.getTutor().getId() : null,
				comanda.getTutor() != null ? comanda.getTutor().getNombreCompleto() : null,
				comanda.getMascota() != null ? comanda.getMascota().getId() : null,
				comanda.getMascota() != null ? comanda.getMascota().getNombre() : null,
				comanda.getConsultaMedica() != null ? comanda.getConsultaMedica().getId() : null,
				comanda.getFechaEmision(),
				toPagoResponseList(comanda.getPagos()),
				comanda.getItems() == null ? List.of() : comanda.getItems().stream()
						.map(item -> new ComandaCobroResponseDTO.ItemDTO(item.getDescripcion(), item.getCantidad(),
								item.getPrecioUnitario(), item.getSubtotal()))
						.toList());
	}

	public List<ComandaCobroResponseDTO> toComandaResponseList(List<ComandaCobro> comandas) {
		if (comandas == null) {
			return List.of();
		}
		return comandas.stream().map(this::toResponse).toList();
	}

	public PagoResponseDTO toPagoResponse(Pago pago) {
		// El pagador es nullable (pagos anteriores a TASK-009) y ademas es LAZY, asi
		// que solo se toca aca adentro, con la transaccion del servicio abierta: leer
		// la relacion desde el controller lanzaria LazyInitializationException.
		var pagador = pago.getTutorPagador();
		return new PagoResponseDTO(
				pago.getId(),
				pago.getMetodoPago() != null ? pago.getMetodoPago().name() : null,
				pago.getMonto(),
				pago.getFechaHora(),
				pagador != null ? pagador.getId() : null,
				pagador != null ? pagador.getNombreCompleto() : null);
	}

	public List<PagoResponseDTO> toPagoResponseList(List<Pago> pagos) {
		if (pagos == null) {
			return List.of();
		}
		return pagos.stream().map(this::toPagoResponse).toList();
	}
}