package com.veterinaria.veterinaria_backend.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Ajuste de esquema para los turnos de urgencia.
 *
 * <p>Una urgencia se registra sin mascota ni profesional, y su ficha y su
 * comanda pueden quedar sin mascota y sin titular hasta completar los datos.
 * Hibernate ({@code ddl-auto=update}) agrega columnas pero nunca relaja un
 * {@code NOT NULL} existente, asi que en una base creada antes de esta version
 * esas columnas siguen siendo obligatorias. Este componente las relaja una sola
 * vez: si la columna ya acepta nulos no hace nada. Solo cambia la restriccion;
 * no reescribe ningun dato.</p>
 */
@Component
@DependsOn("entityManagerFactory")
public class EsquemaUrgencias {

	private static final Logger log = LoggerFactory.getLogger(EsquemaUrgencias.class);

	/** Tabla y columna (todas BIGINT, claves foraneas) que pasan a aceptar nulos. */
	private static final List<String[]> COLUMNAS = List.of(
			new String[] { "turno_clinico", "mascota_id" },
			new String[] { "turno_clinico", "profesional_id" },
			new String[] { "consulta_medica", "mascota_id" },
			new String[] { "comanda_cobro", "tutor_id" });

	private final JdbcTemplate jdbc;

	public EsquemaUrgencias(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@PostConstruct
	void relajarColumnas() {
		for (String[] columna : COLUMNAS) {
			List<String> nulable = jdbc.queryForList("""
					SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
					WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
					""", String.class, columna[0], columna[1]);
			if (!nulable.isEmpty() && "NO".equals(nulable.get(0))) {
				// Los nombres son constantes de esta clase, nunca entrada de usuario.
				jdbc.execute("ALTER TABLE " + columna[0] + " MODIFY COLUMN " + columna[1] + " BIGINT NULL");
				log.info("Columna {}.{} ahora acepta nulos (turnos de urgencia)", columna[0], columna[1]);
			}
		}
	}
}
