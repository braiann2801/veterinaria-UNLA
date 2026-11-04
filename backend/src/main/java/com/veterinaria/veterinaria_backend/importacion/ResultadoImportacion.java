package com.veterinaria.veterinaria_backend.importacion;

/**
 * Contadores de una corrida de importacion.
 *
 * <p>En simulacion los campos "creados" quedan en cero y los "existentes" /
 * "omitidos" reflejan lo que ya esta en la base. En aplicado se completan ambos
 * lados para que el reporte muestre que se toco y que se respeto.</p>
 */
public record ResultadoImportacion(
		int tutoresCreados,
		int tutoresExistentes,
		int mascotasCreadas,
		int mascotasExistentes,
		int vinculosCreados,
		int profesionalesCreados,
		int profesionalesExistentes,
		int consultasCreadas,
		int consultasOmitidas,
		int guarderiasCreadas,
		int guarderiasOmitidas,
		int comandasCreadas,
		int pagosCreados) {
}
