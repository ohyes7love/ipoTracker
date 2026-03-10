package com.ipo.manager.controller;

import com.ipo.manager.service.KisApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
public class StockPriceApiController {

    private final KisApiService kisApiService;

    public StockPriceApiController(KisApiService kisApiService) {
        this.kisApiService = kisApiService;
    }

    @GetMapping("/price/{stockCode}")
    public ResponseEntity<?> getCurrentPrice(@PathVariable String stockCode) {
        if (!kisApiService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "KIS API 키가 설정되지 않았습니다. application.properties에 kis.api.appkey와 kis.api.appsecret을 설정해주세요."));
        }
        try {
            return ResponseEntity.ok(kisApiService.getCurrentPrice(stockCode));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/price/{stockCode}/{date}")
    public ResponseEntity<?> getPriceOnDate(@PathVariable String stockCode, @PathVariable String date) {
        if (!kisApiService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "KIS API 키가 설정되지 않았습니다. application.properties에 kis.api.appkey와 kis.api.appsecret을 설정해주세요."));
        }
        try {
            LocalDate localDate = LocalDate.parse(date);
            return ResponseEntity.ok(kisApiService.getPriceOnDate(stockCode, localDate));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
