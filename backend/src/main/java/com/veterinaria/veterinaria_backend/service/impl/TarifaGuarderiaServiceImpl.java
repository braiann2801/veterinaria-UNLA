package com.veterinaria.veterinaria_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.dto.CotizacionGuarderiaDTO;
import com.veterinaria.veterinaria_backend.dto.TarifaGuarderiaRequestDTO;
import com.veterinaria.veterinaria_backend.dto.TarifaGuarderiaResponseDTO;
import com.veterinaria.veterinaria_backend.entity.TarifaGuarderia;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.repository.TarifaGuarderiaRepository;
import com.veterinaria.veterinaria_backend.service.TarifaGuarderiaService;

/**
 * Precios de guarderia y calculo del monto de una estadia.
 *
 * <p>Tambien siembra los precios al arrancar (como {@code DataInitializer} con las
 * tarifas): sin una fila, ninguna reserva se podria cotizar. Una vez sembrados,
 * los valores de la base mandan y se editan desde la pantalla Tarifas.</p>
 */
@Service
@Transactional(readOnly = true)
public class TarifaGuarderiaServiceImpl implements TarifaGuarderiaService, CommandLineRunner {

	public static final String HORAS = "HORAS";
	public static final String NOCHES = "NOCHES";

	private final TarifaGuarderiaRepository repository;
	private final BigDecimal precioHoraInicial;
	private final BigDecimal precioNocheInicial;

	public TarifaGuarderiaServiceImpl(TarifaGuarderiaRepository repository,
			@Value("${veterinaria.guarderia.precio-hora:2000.00}") BigDecimal precioHoraInicial,
			@Value("${veterinaria.guarderia.precio-noche:10000.00}") BigDecimal precioNocheInicial) {
		this.repository = repository;
		this.precioHoraInicial = precioHoraInicial;
		this.precioNocheInicial = precioNocheInicial;
	}

	@Override
	@Transactional
	public void run(String... args) {
		sembrar();
	}

	@Override
	@Transactional
	public int sembrar() {
		if (repository.findFirstByOrderByIdAsc().isPresent()) {
			return 0;
		}
		TarifaGuarderia tarifa = new TarifaGuarderia();
		tarifa.setPrecioHora(escala(precioHoraInicial));
		tarifa.setPrecioNoche(escala(precioNocheInicial));
		repository.save(tarifa);
		return 1;
	}

	@Override
	public TarifaGuarderiaResponseDTO obtener() {
		TarifaGuarderia tarifa = vigente();
		return new TarifaGuarderiaResponseDTO(tarifa.getPrecioHora(), tarifa.getPrecioNoche());
	}

	@Override
	@Transactional
	public TarifaGuarderiaResponseDTO actualizar(TarifaGuarderiaRequestDTO request) {
		TarifaGuarderia tarifa = repository.findFirstByOrderByIdAsc().orElseGet(TarifaGuarderia::new);
		tarifa.setPrecioHora(escala(request.precioHora()));
		tarifa.setPrecioNoche(escala(request.precioNoche()));
		TarifaGuarderia guardada = repository.save(tarifa);
		return new TarifaGuarderiaResponseDTO(guardada.getPrecioHora(), guardada.getPrecioNoche());
	}

	@Override
	public CotizacionGuarderiaDTO cotizar(LocalDateTime desde, LocalDateTime hasta) {
		if (desde == null || hasta == null) {
			throw new BadRequestException("La entrada y la salida de la estadia son obligatorias");
		}
		if (!hasta.isAfter(desde)) {
			throw new BadRequestException("La salida tiene que ser posterior a la entrada");
		}
		TarifaGuarderia tarifa = vigente();
		// Noches = cambios de fecha del calendario: lunes 9:00 a miercoles 10:00 son 2.
		long noches = ChronoUnit.DAYS.between(desde.toLocalDate(), hasta.toLocalDate());
		if (noches > 0) {
			return cotizacion(NOCHES, noches, tarifa.getPrecioNoche());
		}
		long minutos = Duration.between(desde, hasta).toMinutes();
		long horas = Math.max(1, (minutos + 59) / 60);
		return cotizacion(HORAS, horas, tarifa.getPrecioHora());
	}

	private static CotizacionGuarderiaDTO cotizacion(String modalidad, long cantidad, BigDecimal precio) {
		return new CotizacionGuarderiaDTO(modalidad, cantidad, precio,
				escala(precio.multiply(BigDecimal.valueOf(cantidad))));
	}

	private TarifaGuarderia vigente() {
		return repository.findFirstByOrderByIdAsc().orElseThrow(() -> new BadRequestException(
				"No hay tarifas de guarderia cargadas. El Dueño tiene que cargarlas en Tarifas."));
	}

	private static BigDecimal escala(BigDecimal valor) {
		return valor.setScale(2, RoundingMode.HALF_UP);
	}
}
