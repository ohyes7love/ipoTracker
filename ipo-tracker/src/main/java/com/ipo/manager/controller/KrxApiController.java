package com.ipo.manager.controller;

import com.ipo.manager.service.KrxService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/krx")
public class KrxApiController {

    private final KrxService krxService;

    public KrxApiController(KrxService krxService) {
        this.krxService = krxService;
    }

    @GetMapping("/stocks")
    public ResponseEntity<?> getAllStocks() {
        try {
            return ResponseEntity.ok(krxService.getAllStocks());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
