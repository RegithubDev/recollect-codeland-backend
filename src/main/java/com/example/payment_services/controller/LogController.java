package com.example.payment_services.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Profile("prod")
@RestController
public class LogController {

    @Value("${logging.file.name}")
    private String logFile;

    @GetMapping(value = "/v3/api-docs/logs", produces = "text/plain")
    public String getLogs() throws IOException {
        try (Stream<String> stream = Files.lines(Path.of(logFile))) {
            return stream.collect(Collectors.joining("\n"));
        }
    }
}