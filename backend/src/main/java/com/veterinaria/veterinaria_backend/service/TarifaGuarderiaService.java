package com.veterinaria.veterinaria_backend.service;

import java.time.LocalDateTime;

import com.veterinaria.veterinaria_backend.dto.CotizacionGuarderiaDTO;
import com.veterinaria.veterinaria_backend.dto.TarifaGuarderiaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TarifaGuarderiaResponseDTO;

public interface TarifaGuarderiaService {

	TarifaGuarderiaResponseDTO obtener();

	TarifaGuarderiaResponseDTO actualizar(TarifaGuarderiaRequestDTO request);

	/**
	 * Monto de una estadia: horas x precio por hora si entra y sale el mismo dia
	 * (la fraccion de hora se cobra entera), o noches x precio por noche si pasa
	 * al menos una. Es la unica formula: la usan la pantalla y la comanda.
	 *
	 * @throws com.veterinaria.veterinaria_backend.exception.BadRequestException si la salida no es posterior a la entrada
	 */
	CotizacionGuarderiaDTO cotizar(LocalDateTime desde, LocalDateTime hasta);

	/** Siembra los precios si no hay configuracion. Devuelve 1 si sembro, 0 si ya existia. */
	int sembrar();
}
