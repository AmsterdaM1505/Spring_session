package ru.mephi.shared;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice
public class RestErrors {
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> domain(DomainException ex) {
        return error(ex.status(), ex.code(), ex.getMessage());
    }
    @ExceptionHandler( {
        MethodArgumentNotValidException.class, ConstraintViolationException.class,
        HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiError> validation(Exception ex) {
        return error(400, "VALIDATION_ERROR", "Проверьте обязательные поля, типы и ограничения значений");
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> denied(AccessDeniedException ex) {
        return error(403, "FORBIDDEN", "Недостаточно прав");
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> conflict(DataIntegrityViolationException ex) {
        return error(409, "DATA_CONFLICT", "Операция нарушает уникальность или связи данных");
    }
    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    public ResponseEntity<ApiError> concurrent(org.springframework.dao.ConcurrencyFailureException ex) {
        return error(409, "CONCURRENT_UPDATE", "Конкурирующая операция; повторите запрос с тем же requestId");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex) {
        if (ex instanceof org.springframework.web.ErrorResponse response) {
            return error(response.getStatusCode().value(), "HTTP_ERROR", "Ресурс или метод запроса недоступен");
        }
        LoggerFactory.getLogger(getClass()).error("Unhandled request failure", ex);
        return error(500, "INTERNAL_ERROR", "Внутренняя ошибка сервиса");
    }
    private ResponseEntity<ApiError> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status, code, message, MDC.get("traceId")));
    }
}
