package com.ipo.manager.controller;

import com.ipo.manager.service.DartApiService;
import com.ipo.manager.service.IpoStockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dart")
public class DartApiController {

    private final DartApiService dartApiService;
    private final IpoStockService ipoStockService;

    public DartApiController(DartApiService dartApiService, IpoStockService ipoStockService) {
        this.dartApiService = dartApiService;
        this.ipoStockService = ipoStockService;
    }

    /** 종목명 자동완성: kokstock 일정 객체 반환 (날짜 자동채움용) */
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q) {
        return ResponseEntity.ok(ipoStockService.searchSchedule(q));
    }

    /** 종목 상세: kokstock 팝업 데이터 */
    @GetMapping("/ipo-detail")
    public ResponseEntity<?> ipoDetail(@RequestParam String idx) {
        return ResponseEntity.ok(ipoStockService.getIpoDetail(idx));
    }

    /** 캐시 강제 갱신 */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh() {
        String result = ipoStockService.refreshCache();
        boolean ok = result.startsWith("ok:");
        int count = ok ? Integer.parseInt(result.split(":")[1]) : 0;
        return ok
            ? ResponseEntity.ok(java.util.Map.of("status", "ok", "count", count))
            : ResponseEntity.internalServerError().body(java.util.Map.of("status", "error", "message", result));
    }

    /** 캘린더용: kokstock 스크래핑 */
    @GetMapping("/calendar")
    public ResponseEntity<?> calendar(@RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(ipoStockService.getSchedulesByMonth(year, month));
    }

    /** 디버그: list.json 원본 데이터 (report_nm 확인용) */
    @GetMapping("/debug")
    public ResponseEntity<?> debug() {
        return ResponseEntity.ok(dartApiService.debugRawFilings());
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
