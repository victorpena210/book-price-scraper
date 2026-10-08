package com.victorpena.contacttracker.controller;

import com.victorpena.contacttracker.scraper.ObituaryImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/obituaries/import")
public class ObituaryImportController {
    private final ObituaryImportService imports;
    public ObituaryImportController(ObituaryImportService imports) { this.imports = imports; }

    @GetMapping public ObituaryImportService.Progress status() { return imports.status(); }

    @PostMapping public ResponseEntity<?> start(@Valid @RequestBody ImportRequest request) {
        try {
            return ResponseEntity.accepted().body(imports.start(request.url()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
        } catch (IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
        }
    }

    public record ImportRequest(@NotBlank @Size(max = 300) String url) {}
}
