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

    /**
     * 종목명 자동완성 검색
     * GET /api/krx/search?query=삼성
     * → 네이버 금융 API로 국내 전종목 검색
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String query) {
        try {
            return ResponseEntity.ok(krxService.searchStocks(query));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /** 레거시 호환 - 빈 목록 반환 */
    @GetMapping("/stocks")
    public ResponseEntity<?> getAllStocks() {
        return ResponseEntity.ok(krxService.getAllStocks());
    }
}
