package com.veterinaria.veterinaria_backend.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.veterinaria.veterinaria_backend.entity.ComandaCobro;
import com.veterinaria.veterinaria_backend.entity.Tarifa;
import com.veterinaria.veterinaria_backend.repository.TarifaRepository;

/**
 * Siembra las tarifas base la primera vez que se levanta el sistema.
 *
 * <p><b>Por que existe.</b> Sin una tarifa cargada, {@code POST /api/v1/consultas}
 * y {@code POST /api/v1/comandas} responden 400 ("No hay tarifa cargada para el
 * concepto ..."), asi que no se emite ninguna comanda y la Caja del Dia queda
 * vacia para siempre. La unica forma de cargar precios era
 * {@code PUT /api/v1/comandas/tarifas/{concepto}}, un endpoint de API sin
 * pantalla: el mostrador no tenia como salir de ese estado. Este arranque carga
 * los tres conceptos del vocabulario de {@link ComandaCobro.Concepto} y deja el
 * sistema facturando desde el primer minuto.</p>
 *
 * <p><b>No pisa precios.</b> Solo siembra si la tabla esta vacia. Si el
 * mostrador ya cargo o edito precios, esos mandan: volver a levantar la
 * aplicacion no revierte un cambio de precio hecho a proposito. Por eso el
 * chequeo es {@code count() == 0} y no un upsert.</p>
 *
 * <p><b>Los montos no estan hardcodeados en el servicio.</b> Salen de
 * {@code veterinaria.tarifas.*}, con un valor por defecto razonable. El
 * javadoc de {@link Tarifa} es explicito: hardcodear un precio en el servicio
 * haria imposible cambiarlo sin recompilar. Una vez sembrada, la fila de la
 * base es la unica fuente de verdad y se edita con
 * {@code PUT /api/v1/comandas/tarifas/{concepto}}.</p>
 *
 * <p>El vocabulario se toma del enum compartido de <b>tres</b> conceptos
 * (CONSULTA_CLINICA, GUARDERIA, OTRO). No se inventan conceptos nuevos: el
 * precio de un concepto que ningun flujo emite seria una fila muerta y, peor,
 * sugeriria una capacidad de facturacion que el sistema no tiene.</p>
 */
@Component
public class DataInitializer implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

	private final TarifaRepository tarifaRepository;
	private final BigDecimal montoConsultaClinica;
	private final BigDecimal montoGuarderia;
	private final BigDecimal montoOtro;

	public DataInitializer(
			TarifaRepository tarifaRepository,
			@Value("${veterinaria.tarifas.consulta-clinica:8000.00}") BigDecimal montoConsultaClinica,
			@Value("${veterinaria.tarifas.guarderia:10000.00}") BigDecimal montoGuarderia,
			@Value("${veterinaria.tarifas.otro:5000.00}") BigDecimal montoOtro) {
		this.tarifaRepository = tarifaRepository;
		this.montoConsultaClinica = montoConsultaClinica;
		this.montoGuarderia = montoGuarderia;
		this.montoOtro = montoOtro;
	}

	@Override
	@Transactional
	public void run(String... args) {
		sembrar();
	}

	/**
	 * Carga los conceptos base si la tabla de tarifas esta vacia.
	 *
	 * <p>Publico y separado de {@link #run} para poder verificarlo en un test de
	 * forma determinista: al depender de {@code count() == 0}, comprobar la
	 * siembra a traves del arranque del contexto seria fragil, porque otro test
	 * puede haber borrado las tarifas antes.</p>
	 *
	 * @return cuantas tarifas se sembraron; 0 si ya habia precios cargados
	 */
	public int sembrar() {
		long existentes = tarifaRepository.count();
		if (existentes > 0) {
			log.info("Tarifas ya cargadas ({}). No se siembra: los precios vigentes no se pisan.", existentes);
			return 0;
		}

		List<Tarifa> base = List.of(
				tarifa(ComandaCobro.Concepto.CONSULTA_CLINICA, montoConsultaClinica, "Consulta clinica"),
				tarifa(ComandaCobro.Concepto.GUARDERIA, montoGuarderia, "Guarderia"),
				tarifa(ComandaCobro.Concepto.OTRO, montoOtro, "Concepto generico"));

		tarifaRepository.saveAll(base);
		log.info("Semilla de tarifas: {} conceptos base cargados.", base.size());
		return base.size();
	}

	private static Tarifa tarifa(ComandaCobro.Concepto concepto, BigDecimal monto, String descripcion) {
		Tarifa tarifa = new Tarifa();
		tarifa.setConcepto(concepto);
		tarifa.setMonto(monto.setScale(2, RoundingMode.HALF_UP));
		tarifa.setDescripcion(descripcion);
		tarifa.setVigente(true);
		return tarifa;
	}
}