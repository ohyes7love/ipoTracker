package com.ipo.manager.controller;

import com.ipo.manager.domain.IpoStock;
import com.ipo.manager.dto.IpoDto;
import com.ipo.manager.dto.MonthlySummaryDto;
import com.ipo.manager.service.IpoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IpoApiController - 공모주 청약 내역 REST API
 *
 * <p>Base URL: /api/ipo</p>
 *
 * <pre>
 *   GET    /api/ipo?year=2026        → 연도별 청약 내역 목록 (상장일 기준 정렬)
 *   GET    /api/ipo/all              → 전체 청약 내역
 *   POST   /api/ipo                  → 청약 내역 등록
 *   PUT    /api/ipo/{id}             → 청약 내역 수정
 *   DELETE /api/ipo/{id}             → 청약 내역 삭제
 *   GET    /api/ipo/monthly?year=    → 월별 수익 집계
 *   GET    /api/ipo/stocks           → 종목명 자동완성용 목록
 * </pre>
 */
@RestController
@RequestMapping("/api/ipo")
public class IpoApiController {

    private final IpoService ipoService;

    public IpoApiController(IpoService ipoService) {
        this.ipoService = ipoService;
    }

    /** 전체 청약 내역 (연도 무관) */
    @GetMapping("/all")
    public List<IpoDto> all() {
        return ipoService.getAll();
    }

    /**
     * 연도별 청약 내역 조회
     *
     * @param year 조회 연도 (필수)
     */
    @GetMapping
    public List<IpoDto> list(@RequestParam int year) {
        return ipoService.getByYear(year);
    }

    /**
     * 청약 내역 등록
     * <p>저장 시 ipo_stock 테이블에 종목명+공모가를 upsert하여 자동완성에 반영합니다.</p>
     */
    @PostMapping
    public IpoDto create(@RequestBody IpoDto dto) {
        return ipoService.create(dto);
    }

    /** 청약 내역 수정 */
    @PutMapping("/{id}")
    public IpoDto update(@PathVariable Long id, @RequestBody IpoDto dto) {
        return ipoService.update(id, dto);
    }

    /** 청약 내역 삭제 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ipoService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 월별 수익 집계
     * <p>매도 완료(soldDate 존재)된 건만 집계합니다.</p>
     *
     * @param year 조회 연도
     * @return 1~12월 수익 합계 배열
     */
    @GetMapping("/monthly")
    public List<MonthlySummaryDto> monthly(@RequestParam int year) {
        return ipoService.getMonthlySummary(year);
    }

    /** 자동완성용 종목 목록 (종목명 + 확정공모가) */
    @GetMapping("/stocks")
    public List<IpoStock> stocks() {
        return ipoService.getAllStocks();
    }

    /** 종목 수동 등록 */
    @PostMapping("/stocks")
    public IpoStock createStock(@RequestBody IpoStock stock) {
        return ipoService.createStock(stock);
    }
}
