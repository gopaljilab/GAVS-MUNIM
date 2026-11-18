package org.munim.api;

import jakarta.servlet.http.HttpServletRequest;
import org.munim.financial.FinancialEvaluator.IncompleteQuoteException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;
import java.util.UUID;
import java.util.NoSuchElementException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.DuplicateKeyException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiDtos.Problem> invalid(MethodArgumentNotValidException e, HttpServletRequest r) {
        var fields=new java.util.HashMap<String,String>();
        e.getBindingResult().getFieldErrors().forEach(x->fields.put(x.getField(),x.getDefaultMessage()));
        return problem(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Request validation failed",fields);
    }
    @ExceptionHandler(IncompleteQuoteException.class)
    ResponseEntity<ApiDtos.Problem> incomplete(IncompleteQuoteException e) {
        var fields=new java.util.TreeMap<String,String>();e.missing().forEach(k->fields.put(k,"Required or unresolved"));
        return problem(HttpStatus.UNPROCESSABLE_ENTITY,"QUOTE_INCOMPLETE","Quote cannot be evaluated",fields);
    }
    @ExceptionHandler(org.munim.profitability.ProfitabilityValidationException.class)
    ResponseEntity<ApiDtos.Problem> profitabilityValidation(org.munim.profitability.ProfitabilityValidationException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getErrorCode(), e.getMessage(), Map.of());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiDtos.Problem> bad(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",e.getMessage(),Map.of());
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiDtos.Problem> conflict(IllegalStateException e) {
        return problem(HttpStatus.CONFLICT,"STATE_CONFLICT",e.getMessage(),Map.of());
    }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiDtos.Problem> missing(NoSuchElementException e) {
        return problem(HttpStatus.NOT_FOUND,"NOT_FOUND",e.getMessage(),Map.of());
    }
    @ExceptionHandler(EmptyResultDataAccessException.class)
    ResponseEntity<ApiDtos.Problem> rowMissing(EmptyResultDataAccessException e) {
        return problem(HttpStatus.NOT_FOUND,"NOT_FOUND","Requested record was not found",Map.of());
    }
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ApiDtos.Problem> duplicate(DuplicateKeyException e) {
        return problem(HttpStatus.CONFLICT,"DUPLICATE_RESOURCE","A matching record already exists",Map.of());
    }
    @ExceptionHandler(SecurityException.class)
    ResponseEntity<ApiDtos.Problem> forbidden(SecurityException e) {
        return problem(HttpStatus.FORBIDDEN,"FORBIDDEN",e.getMessage(),Map.of());
    }
    @ExceptionHandler(org.munim.ai.AiProviderException.class)
    ResponseEntity<ApiDtos.Problem> aiError(org.munim.ai.AiProviderException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode());
        if (status == null) status = HttpStatus.BAD_GATEWAY;
        return problem(status, "AI_PROVIDER_ERROR", e.getMessage(), Map.of("provider", e.getProvider()));
    }
    private ResponseEntity<ApiDtos.Problem> problem(HttpStatus status,String code,String detail,Map<String,String> fields){
        return ResponseEntity.status(status).contentType(MediaType.valueOf("application/problem+json")).body(new ApiDtos.Problem("about:blank",status.getReasonPhrase(),status.value(),detail,code,fields,UUID.randomUUID().toString()));
    }
}
