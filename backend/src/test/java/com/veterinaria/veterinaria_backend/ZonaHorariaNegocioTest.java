package com.veterinaria.veterinaria_backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.veterinaria.veterinaria_backend.entity.Pago;

/**
 * Las horas del negocio son las de Argentina aunque el servidor corra en UTC
 * (Docker, nube): un cobro de las 20:56 no puede quedar a las 23:56 ni pasar a
 * la jornada del dia siguiente desde las 21:00.
 *
 * <p>El bloque estatico simula ese servidor: corre antes de que se arme el
 * contexto. La propiedad explicita da a esta clase un contexto propio, asi que
 * la zona se aplica al arrancarlo y no se reusa uno ya creado.</p>
 */
@SpringBootTest(properties = "veterinaria.zona-horaria=America/Argentina/Buenos_Aires")
class ZonaHorariaNegocioTest {

	private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

	static {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	@Autowired
	private Clock reloj;

	@Test
	@DisplayName("Con el servidor en UTC, la aplicacion arranca con la zona horaria del negocio")
	void zonaDelNegocio() {
		assertThat(TimeZone.getDefault().getID()).isEqualTo("America/Argentina/Buenos_Aires");
		assertThat(reloj.getZone()).isEqualTo(ARGENTINA);
	}

	@Test
	@DisplayName("La hora de un pago nuevo es la hora local argentina")
	void horaDelPago() {
		LocalDateTime enArgentina = LocalDateTime.now(ARGENTINA);
		LocalDateTime delPago = new Pago().getFechaHora();
		assertThat(Duration.between(enArgentina, delPago).abs()).isLessThan(Duration.ofMinutes(1));
	}
}
