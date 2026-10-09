package com.eljaibo.pos.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException exception,
                                          HttpServletRequest request) {
        return requestProblem(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableRequest(HttpMessageNotReadableException exception,
                                                HttpServletRequest request) {
        // Parsing diagnostics can include request values, including a password.
        return requestProblem(HttpStatus.BAD_REQUEST, "Invalid request body", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleMissingResource(NoResourceFoundException exception,
                                              HttpServletRequest request) {
        return requestProblem(HttpStatus.NOT_FOUND, "Resource not found", request);
    }

    private ProblemDetail requestProblem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    /*
     * Excepciones que nosotros lanzamos intencionalmente desde
     * application/services.
     *
     * Ejemplo:
     * new ResponseStatusException(
     *     HttpStatus.CONFLICT,
     *     "Presentation name already exists"
     * )
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status =
                HttpStatus.valueOf(exception.getStatusCode().value());

        log.warn(
                "Request rejected: method={}, path={}, status={}, reason={}",
                request.getMethod(),
                request.getRequestURI(),
                status.value(),
                exception.getReason()
        );

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        status,
                        exception.getReason() != null
                                ? exception.getReason()
                                : status.getReasonPhrase()
                );

        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(
                URI.create(request.getRequestURI())
        );

        return problem;
    }

    /*
     * Validaciones de @Valid en Request DTOs.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> errors =
                new LinkedHashMap<>();

        for (FieldError fieldError :
                exception.getBindingResult().getFieldErrors()) {

            errors.put(
                    fieldError.getField(),
                    fieldError.getDefaultMessage()
            );
        }

        log.warn(
                "Validation failed: method={}, path={}, errors={}",
                request.getMethod(),
                request.getRequestURI(),
                errors
        );

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        "Request validation failed"
                );

        problem.setTitle("Validation error");
        problem.setInstance(
                URI.create(request.getRequestURI())
        );

        problem.setProperty(
                "errors",
                errors
        );

        return problem;
    }

    /*
     * Validaciones Jakarta que ocurran fuera de un @RequestBody,
     * por ejemplo parámetros/path variables.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Constraint violation: method={}, path={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                exception.getMessage()
        );

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        exception.getMessage()
                );

        problem.setTitle("Validation error");
        problem.setInstance(
                URI.create(request.getRequestURI())
        );

        return problem;
    }

    /*
     * Restricciones de base de datos:
     * unique constraints, foreign keys, etc.
     *
     * No regresamos el mensaje SQL al cliente porque puede
     * exponer detalles internos.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        log.error(
                "Database integrity violation: method={}, path={}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        "The operation conflicts with existing data"
                );

        problem.setTitle("Data conflict");
        problem.setInstance(
                URI.create(request.getRequestURI())
        );

        return problem;
    }

    /*
     * Última barrera.
     *
     * Aquí sí queremos stacktrace completo.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "Unexpected error: method={}, path={}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "An unexpected error occurred"
                );

        problem.setTitle("Internal server error");
        problem.setInstance(
                URI.create(request.getRequestURI())
        );

        return problem;
    }
}
