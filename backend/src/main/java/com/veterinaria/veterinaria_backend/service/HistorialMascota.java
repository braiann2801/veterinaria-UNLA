package com.veterinaria.veterinaria_backend.service;

import org.springframework.stereotype.Component;

import com.veterinaria.veterinaria_backend.repository.ComandaCobroRepository;
import com.veterinaria.veterinaria_backend.repository.ConsultaMedicaRepository;
import com.veterinaria.veterinaria_backend.repository.ReservaGuarderiaRepository;
import com.veterinaria.veterinaria_backend.repository.TurnoClinicoRepository;

/**
 * Consulta si una mascota tiene registros que impiden borrarla del padron.
 *
 * <p>El ABM no puede eliminar una mascota con actividad clinica (turnos,
 * consultas), de guarderia (reservas) o contable (comandas): la clave foranea
 * lo impediria en la base con un error crudo de integridad. Centralizar la
 * lista de dependencias evita que el servicio de mascotas y el de tutores
 * (cuando borra una mascota que quedo huerfana) tengan cada uno su propia
 * version y se desincronicen.</p>
 */
@Component
public class HistorialMascota {

	private final TurnoClinicoRepository turnoClinicoRepository;
	private final ReservaGuarderiaRepository reservaGuarderiaRepository;
	private final ConsultaMedicaRepository consultaMedicaRepository;
	private final ComandaCobroRepository comandaCobroRepository;

	public HistorialMascota(TurnoClinicoRepository turnoClinicoRepository,
			ReservaGuarderiaRepository reservaGuarderiaRepository,
			ConsultaMedicaRepository consultaMedicaRepository,
			ComandaCobroRepository comandaCobroRepository) {
		this.turnoClinicoRepository = turnoClinicoRepository;
		this.reservaGuarderiaRepository = reservaGuarderiaRepository;
		this.consultaMedicaRepository = consultaMedicaRepository;
		this.comandaCobroRepository = comandaCobroRepository;
	}

	/** {@code true} si la mascota tiene turnos, reservas, consultas o comandas. */
	public boolean tieneHistorial(Long mascotaId) {
		return turnoClinicoRepository.existsByMascotaId(mascotaId)
				|| reservaGuarderiaRepository.existsByMascotaId(mascotaId)
				|| consultaMedicaRepository.existsByMascotaId(mascotaId)
				|| comandaCobroRepository.existsByMascotaId(mascotaId);
	}
}
