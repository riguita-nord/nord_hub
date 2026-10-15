package com.nordlab.hub.api;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException ex){HttpStatus status=HttpStatus.valueOf(ex.getStatusCode().value());String message=ex.getReason()==null?status.getReasonPhrase():ex.getReason();return ResponseEntity.status(status).body(body(status,message));}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException ex){String message=ex.getBindingResult().getFieldErrors().stream().findFirst().map(e->e.getField()+" "+e.getDefaultMessage()).orElse("Request validation failed");return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST,message));}
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<?> constraint(ConstraintViolationException ex){return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST,"Request validation failed"));}
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(DataIntegrityViolationException ex){return ResponseEntity.status(HttpStatus.CONFLICT).body(body(HttpStatus.CONFLICT,"The request conflicts with existing data"));}
    private Map<String,Object> body(HttpStatus status,String message){return Map.of("status",status.value(),"error",status.getReasonPhrase(),"message",message,"timestamp",Instant.now().toString());}
}
