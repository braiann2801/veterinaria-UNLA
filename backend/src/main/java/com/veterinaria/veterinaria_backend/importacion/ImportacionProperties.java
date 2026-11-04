package com.veterinaria.veterinaria_backend.importacion;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuracion de la importacion unica del Excel historico.
 *
 * <p>Deshabilitada por defecto: el runner solo actua cuando
 * {@code veterinaria.importacion.habilitada=true}. {@code aplicar=false} hace
 * una simulacion que genera el reporte sin escribir en la base.</p>
 */
@Component
@ConfigurationProperties(prefix = "veterinaria.importacion")
@Getter
@Setter
public class ImportacionProperties {

	private boolean habilitada = false;

	private String archivo = "";

	private boolean aplicar = false;
}
