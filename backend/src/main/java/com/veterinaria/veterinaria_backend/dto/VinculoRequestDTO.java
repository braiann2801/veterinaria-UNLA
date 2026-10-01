package com.veterinaria.veterinaria_backend.dto;

/**
 * Body alternativo del endpoint de vinculacion, para enviar el flag por cuerpo.
 *
 * <p>El flag se puede pasar por body o por query param
 * ({@code ?autorizadoRetiro=true}). La regla de negocio 2.4 exige que cada
 * tupla registre el flag de forma explicita, asi que si llega el body vacio y no
 * hay query param, el controller responde 400 en lugar de asumir un valor.</p>
 */
public record VinculoRequestDTO(Boolean autorizadoRetiro) {
}