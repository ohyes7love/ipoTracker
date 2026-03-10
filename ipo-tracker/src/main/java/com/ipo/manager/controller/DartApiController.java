package com.ipo.manager.controller;

import com.ipo.manager.service.DartApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dart")
public class DartApiController {

    private final DartApiService dartApiService;

    public DartApiController(DartApiService dartApiService) {
        this.dartApiService = dartApiService;
    }

    @GetMapping("/ipos")
    public ResponseEntity<?> getIpos() {
        if (!dartApiService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "DART API 키가 설정되지 않았습니다. application.properties에 dart.api.key를 설정해주세요."));
        }
        try {
            return ResponseEntity.ok(dartApiService.getRecentIpoList());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
