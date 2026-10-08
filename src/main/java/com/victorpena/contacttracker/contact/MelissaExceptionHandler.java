package com.victorpena.contacttracker.contact;

import com.victorpena.contacttracker.controller.MelissaTestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice(assignableTypes = {MelissaTestController.class, SavedPeopleMelissaController.class})
public class MelissaExceptionHandler {
    @ExceptionHandler(MelissaSearchException.class)
    public ResponseEntity<LookupError> melissaFailure(MelissaSearchException exception) {
        HttpStatus status = exception.getError().equals("CONFIGURATION_ERROR")
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(new LookupError(
                exception.getError(), exception.getMessage(), exception.getResultCodes()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<LookupError> invalidInput(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new LookupError(
                "INVALID_INPUT", exception.getMessage(), List.of()));
    }

    public record LookupError(String error, String message, List<String> resultCodes) {
    }
}
