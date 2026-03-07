package com.example.payment_services.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Profile("prod")
@RestController
@Tag(name = "View Logs")
public class LogController {

    @Value("${logging.file.name}")
    private String logFile;

    @GetMapping(value = "/v3/api-docs/logs", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getLogs(@RequestParam(required = false) Integer lines) {
        try (final Stream<String> stream = Files.lines(Path.of(logFile))) {
            if (null == lines || lines <= 0) {
                return stream.collect(Collectors.joining("\n"));
            }

            final Deque<String> buffer = new ArrayDeque<>(lines);
            stream.forEach(line -> {
                if (buffer.size() == lines) {
                    buffer.removeFirst();
                }
                buffer.addLast(line);
            });

            return String.join("\n", buffer);
        } catch (IOException e) {
            return null;
        }
    }
}