package com.veterinaria.veterinaria_backend.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.veterinaria.veterinaria_backend.exception.ApiErrorResponse;
import com.veterinaria.veterinaria_backend.exception.BadRequestException;
import com.veterinaria.veterinaria_backend.exception.BusinessConflictException;
import com.veterinaria.veterinaria_backend.exception.ResourceNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Manejo centralizado de errores. Devuelve siempre el mismo JSON
 * {@link ApiErrorResponse} para que el frontend no tenga que parsear variantes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** 400: peticion mal formada o regla de negocio violada. */
	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ApiErrorResponse> handleBadRequest(BadRequestException ex,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request",
						ex.getMessage(), request.getRequestURI()));
	}

	/** 404: el recurso o el vinculo solicitado no existe. */
	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiErrorResponse.of(HttpStatus.NOT_FOUND.value(), "Not Found",
						ex.getMessage(), request.getRequestURI()));
	}

	/**
	 * 409: conflicto de reglas de negocio. Aforo de guarderia completo o turno
	 * solapado. El mensaje ya es descriptivo (arma en el servicio) y se devuelve
	 * tal cual porque el frontend necesita saber el detalle (cupos, horario en
	 * conflicto, profesional).
	 */
	@ExceptionHandler(BusinessConflictException.class)
	public ResponseEntity<ApiErrorResponse> handleConflict(BusinessConflictException ex,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(ApiErrorResponse.of(HttpStatus.CONFLICT.value(), "Conflict",
						ex.getMessage(), request.getRequestURI()));
	}

	/**
	 * 400: falla {@code @Valid} en el body. Devuelve el detalle por campo para
	 * que el formulario del frontend pueda marcar el input correcto.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
				.map(FieldError::getDefaultMessage)
				.distinct()
				.toList();

		String mensaje = detalles.isEmpty()
				? "La peticion no supero la validacion"
				: "La peticion no supero la validacion: " + String.join("; ", detalles);

		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request",
						mensaje, request.getRequestURI(), detalles));
	}

	/** 400: id o query param con tipo invalido, o body ilegible. */
	@ExceptionHandler({ MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class })
	public ResponseEntity<ApiErrorResponse> handleUnreadable(Exception ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request",
						"La peticion no pudo interpretarse: " + ex.getMessage(),
						request.getRequestURI()));
	}

	/**
	 * 400: falta un query param obligatorio.
	 *
	 * <p>Sin este handler, omitir un parametro required devolvia 500. No es solo
	 * una cuestion de status: un 500 hace pensar al operador que el servidor esta
	 * roto, cuando lo que paso es que el formulario no envio el campo.</p>
	 */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "Bad Request",
						"Falta el parametro obligatorio '" + ex.getParameterName() + "'",
						request.getRequestURI()));
	}

	/** 500: no se filtra la traza interna al cliente. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		// La traza va al log del servidor, nunca al cliente.
		log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);

		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
						"Internal Server Error",
						"Ocurrio un error inesperado. Intentelo nuevamente.",
						request.getRequestURI()));
	}
}