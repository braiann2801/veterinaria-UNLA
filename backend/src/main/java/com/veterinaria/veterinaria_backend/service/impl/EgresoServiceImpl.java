package com.veterinaria.veterinaria_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.DeudaTutorResponseDTO;
import com.veterinaria.veterinaria_backend.dto.EgresoValidacionResponseDTO;
import com.veterinaria.veterinaria_backend.dto.EgresoValidacionResponseDTO.MotivoBloqueo;
import com.veterinaria.veterinaria_backend.entity.ConsultaMedica;
import com.veterinaria.veterinaria_backend.entity.Mascota;
import com.veterinaria.veterinaria_backend.entity.Tutor;
import com.veterinaria.veterinaria_backend.entity.TutorMascota;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;
import com.veterinaria.veterinaria_backend.mapper.EntityMapper;
import com.veterinaria.veterinaria_backend.repository.ConsultaMedicaRepository;
import com.veterinaria.veterinaria_backend.repository.MascotaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorMascotaRepository;
import com.veterinaria.veterinaria_backend.repository.TutorRepository;
import com.veterinaria.veterinaria_backend.service.ComandaCobroService;
import com.veterinaria.veterinaria_backend.service.EgresoService;

/**
 * Circuito de egreso seguro.
 *
 * <p>La consulta es de solo lectura y no cambia nada: el mostrador consulta, ve
 * el semaforo y decide. Registrar el egreso efectivo es otra operacion, porque
 * una consulta de "¿puedo?" jamas debe dejar rastro de que se hizo.</p>
 *
 * <p>El estado contable no se consulta con una query propia contra
 * {@code comanda_cobro}: se delega en {@link ComandaCobroService#consultarDeuda}
 * para que el saldo que ve el mostrador sea exactamente el mismo que ve el
 * modulo de caja. Duplicar esa suma en dos lugares es como aparecen dos
 * morosidad distintas para la misma persona.</p>
 */
@Service
@Transactional(readOnly = true)
public class EgresoServiceImpl implements EgresoService {

	/** Cero con escala de dinero, para que el mapa del frontend no mezcle 0 y 0.00. */
	private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

	private final MascotaRepository mascotaRepository;
	private final TutorRepository tutorRepository;
	private final TutorMascotaRepository vinculoRepository;
	private final ConsultaMedicaRepository consultaRepository;
	private final ComandaCobroService comandaService;

	public EgresoServiceImpl(MascotaRepository mascotaRepository,
			TutorRepository tutorRepository,
			TutorMascotaRepository vinculoRepository,
			ConsultaMedicaRepository consultaRepository,
			ComandaCobroService comandaService) {
		this.mascotaRepository = mascotaRepository;
		this.tutorRepository = tutorRepository;
		this.vinculoRepository = vinculoRepository;
		this.consultaRepository = consultaRepository;
		this.comandaService = comandaService;
	}

	@Override
	public EgresoValidacionResponseDTO validarPorDni(Long mascotaId, String tutorDni) {
		if (tutorDni == null || tutorDni.isBlank()) {
			throw new BadRequestException("El DNI del tutor es obligatorio");
		}
		// findByDni y no un filtro en memoria: el DNI es la identidad con la que
		// llega la persona al mostrador, y no admitimos espacios ni mayusculas
		// distintas a como la guardo el alta.
		Tutor tutor = tutorRepository.findByDni(tutorDni.trim())
				.orElseThrow(() -> ResourceNotFoundException.tutorPorDni(tutorDni.trim()));
		return validar(mascotaId, tutor);
	}

	@Override
	public EgresoValidacionResponseDTO validarPorTutorId(Long mascotaId, Long tutorId) {
		if (tutorId == null) {
			throw new BadRequestException("El id del tutor es obligatorio");
		}
		return validar(mascotaId, tutorRepository.findById(tutorId)
				.orElseThrow(() -> ResourceNotFoundException.tutor(tutorId)));
	}

	/**
	 * Aplica las reglas en orden de precedencia.
	 *
	 * <p>Cada regla se evalua siempre, aunque una anterior ya haya bloqueado: el
	 * mostrador quiere saber las dos cosas. Si solo se short-circuitara en el
	 * primer bloqueo, el recepcionista veria "no autorizado" y no sabria que
	 * ademas hay una deuda; corregir la autorizacion no destrabaria el egreso y
	 * el ticket volveria al mostrador dos veces.</p>
	 */
	private EgresoValidacionResponseDTO validar(Long mascotaId, Tutor tutor) {
		Mascota mascota = obtenerMascota(mascotaId);

		boolean autorizado = esTutorAutorizado(mascotaId, tutor.getId());

		DeudaTutorResponseDTO deuda = comandaService.consultarDeuda(tutor.getId());
		boolean tieneDeuda = Boolean.TRUE.equals(deuda.tieneDeuda());
		BigDecimal saldo = normalizar(deuda.totalDeuda());

		UltimaConsulta ultima = ultimaConsulta(mascotaId);

		// El animal se entrega con precaución, nunca se retiene por su conducta:
		// la alerta es información para el que recibe, no un impedimento.
		boolean alertaConducta = conductaDeRiesgo(mascota) || ultima.alerta();

		MotivoBloqueo motivo;
		String mensaje;
		if (!autorizado) {
			motivo = MotivoBloqueo.TUTOR_NO_AUTORIZADO;
			mensaje = "El DNI presentado no está autorizado a retirar a "
					+ mascota.getNombre() + ". Autorización: regla 2.4 (vínculo Tutor-Mascota).";
		} else if (tieneDeuda) {
			motivo = MotivoBloqueo.DEUDA_PENDIENTE;
			mensaje = formatear(saldo) + " pendientes en "
					+ deuda.cantidadComandas() + " comanda(s) a nombre de "
					+ tutor.getNombreCompleto() + ".";
		} else {
			motivo = MotivoBloqueo.NINGUNO;
			mensaje = "Retiro habilitado para " + mascota.getNombre() + ".";
		}

		if (motivo == MotivoBloqueo.NINGUNO && alertaConducta) {
			mensaje += " Entregar con precaución: " + descripcionDeAlerta(mascota, ultima) + ".";
		}

		return new EgresoValidacionResponseDTO(
				motivo == MotivoBloqueo.NINGUNO,
				motivo,
				autorizado,
				tieneDeuda,
				saldo,
				alertaConducta,
				conductaDeApi(mascota),
				ultima.conducta(),
				ultima.fecha(),
				mensaje,
				mascota.getId(),
				mascota.getNombre(),
				tutor.getId(),
				tutor.getNombreCompleto());
	}

	// ------------------------------------------------------------- reglas

	private boolean esTutorAutorizado(Long mascotaId, Long tutorId) {
		// El vinculo puede no existir (es un tercero que nunca lo firmo) o
		// existir con autorizadoRetiro=false (cotutor). En los dos casos la
		// respuesta de negocio es la misma: no autorizado. La diferencia solo
		// importa para diagnosticar, no para el semaforo.
		Optional<TutorMascota> vinculo = vinculoRepository.findByTutorIdAndMascotaId(tutorId, mascotaId);
		return vinculo.isPresent() && vinculo.get().isAutorizadoRetiro();
	}

	private UltimaConsulta ultimaConsulta(Long mascotaId) {
		List<ConsultaMedica> fichas = consultaRepository.findByMascotaIdOrderByFechaAtencionDesc(mascotaId);
		if (fichas.isEmpty()) {
			return UltimaConsulta.ninguna();
		}
		ConsultaMedica ficha = fichas.get(0);
		String conducta = ficha.getConductaObservada() == null
				? null
				: ficha.getConductaObservada().name();
		return new UltimaConsulta(conducta, ficha.isAlertaConducta(), ficha.getFechaAtencion());
	}

	private boolean conductaDeRiesgo(Mascota mascota) {
		return mascota.getConducta() == Mascota.Conducta.GRUNE
				|| mascota.getConducta() == Mascota.Conducta.MUERDE;
	}

	/** Frase que completa "Entregar con precaución: ...", sin punto final. */
	private String descripcionDeAlerta(Mascota mascota, UltimaConsulta ultima) {
		if (ultima.conducta() != null) {
			return "en la última atención (" + ultima.fecha().toLocalDate()
					+ ") se registró conducta " + ultima.conducta();
		}
		if (mascota.getConducta() == Mascota.Conducta.MUERDE) {
			return "muerde según su ficha maestra";
		}
		if (mascota.getConducta() == Mascota.Conducta.GRUNE) {
			return "gruñe según su ficha maestra";
		}
		return "su conducta está desactualizada";
	}

	// ------------------------------------------------------------- helpers

	private Mascota obtenerMascota(Long mascotaId) {
		if (mascotaId == null) {
			throw new BadRequestException("El id de la mascota es obligatorio");
		}
		return mascotaRepository.findById(mascotaId)
				.orElseThrow(() -> ResourceNotFoundException.mascota(mascotaId));
	}

	/**
	 * Conducta en el vocabulario de la API.
	 *
	 * <p>Delega en {@link EntityMapper#conductaToApi} en vez de repetir el
	 * {@code switch}: el enum JPA es ASCII (GRUNE) y la API habla con tilde, y
	 * dos copias de esa traduccion divergen en cuanto una se toca.</p>
	 */
	private String conductaDeApi(Mascota mascota) {
		return EntityMapper.conductaToApi(mascota.getConducta());
	}

	private BigDecimal normalizar(BigDecimal valor) {
		return valor == null ? CERO : valor.setScale(2, RoundingMode.HALF_UP);
	}

	private String formatear(BigDecimal monto) {
		return "$" + monto.setScale(2, RoundingMode.HALF_UP)
				.toBigInteger().toString().replaceAll("\\B(?=(\\d{3})+(?!\\d))", ".");
	}

	/**
	 * Datos de la ultima ficha clinica de la mascota.
	 *
	 * <p>Se toma solo la mas reciente a proposito: una ficha vieja con conducta
	 * REACTIVO no describe al animal que esta en el mostrador hoy, y mantenerla
	 * como alerta para siempre dejaria a los perros vetustos marcados para
	 * siempre.</p>
	 */
	private record UltimaConsulta(String conducta, boolean alerta, java.time.LocalDateTime fecha) {

		static UltimaConsulta ninguna() {
			return new UltimaConsulta(null, false, null);
		}
	}
}