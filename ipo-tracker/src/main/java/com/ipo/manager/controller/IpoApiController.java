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
 * <p>공모주 청약 내역의 등록, 조회, 수정, 삭제 및 수익 집계 기능을 제공하는 REST 컨트롤러입니다.
 * 프론트엔드 화면에서 청약 내역을 관리하고 월별 수익 차트 데이터를 제공하는 역할을 담당합니다.</p>
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
 *   PATCH  /api/ipo/{id}/withdrawn   → 출금완료 여부 업데이트
 * </pre>
 */
@RestController
@RequestMapping("/api/ipo")
public class IpoApiController {

    /** 공모주 청약 내역 비즈니스 로직을 처리하는 서비스 */
    private final IpoService ipoService;

    /**
     * IpoApiController 생성자
     *
     * @param ipoService 공모주 청약 내역 서비스 (Spring DI로 주입)
     */
    public IpoApiController(IpoService ipoService) {
        this.ipoService = ipoService;
    }

    /**
     * 전체 청약 내역 조회 (연도 무관)
     *
     * <p>연도 필터 없이 DB에 저장된 모든 청약 내역을 반환합니다.
     * ID 내림차순으로 정렬되어 최신 항목이 상단에 표시됩니다.</p>
     *
     * @return 전체 청약 내역 목록 ({@link IpoDto} 리스트)
     */
    @GetMapping("/all")
    public List<IpoDto> all() {
        return ipoService.getAll();
    }

    /**
     * 연도별 청약 내역 조회
     *
     * <p>특정 연도의 청약 내역만 필터링하여 반환합니다.
     * {@code subscription_year} 컬럼 기준으로 조회하며 ID 내림차순으로 정렬됩니다.</p>
     *
     * @param year 조회할 연도 (필수, 예: 2026)
     * @return 해당 연도의 청약 내역 목록 ({@link IpoDto} 리스트)
     */
    @GetMapping
    public List<IpoDto> list(@RequestParam int year) {
        return ipoService.getByYear(year);
    }

    /**
     * 청약 내역 등록
     *
     * <p>새로운 공모주 청약 내역을 DB에 저장합니다.
     * 저장 시 {@code ipo_stock} 테이블에 종목명+공모가를 upsert하여
     * 자동완성 기능 및 공모가 자동채움에 반영합니다.</p>
     *
     * @param dto 등록할 청약 내역 데이터 (Request Body)
     * @return 저장된 청약 내역 (생성된 ID 포함)
     */
    @PostMapping
    public IpoDto create(@RequestBody IpoDto dto) {
        return ipoService.create(dto);
    }

    /**
     * 청약 내역 수정
     *
     * <p>기존 청약 내역을 수정합니다. 수정 후 {@code ipo_stock} 테이블도 함께 동기화됩니다.</p>
     *
     * @param id  수정할 청약 내역의 고유 ID (Path Variable)
     * @param dto 수정할 청약 내역 데이터 (Request Body)
     * @return 수정된 청약 내역
     * @throws RuntimeException 해당 ID의 레코드가 존재하지 않는 경우
     */
    @PutMapping("/{id}")
    public IpoDto update(@PathVariable Long id, @RequestBody IpoDto dto) {
        return ipoService.update(id, dto);
    }

    /**
     * 청약 내역 삭제
     *
     * <p>지정한 ID의 청약 내역을 DB에서 삭제합니다.</p>
     *
     * @param id 삭제할 청약 내역의 고유 ID (Path Variable)
     * @return HTTP 200 OK (본문 없음)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        ipoService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 출금완료 여부 업데이트
     *
     * <p>청약 수익금의 출금 완료 여부만 부분 업데이트합니다.
     * {@code withdrawn} 필드만 변경하는 PATCH 방식을 사용하여 불필요한 전체 업데이트를 방지합니다.</p>
     *
     * @param id        업데이트할 청약 내역의 고유 ID (Path Variable)
     * @param withdrawn 출금완료 여부 (true: 출금완료, false: 미출금)
     * @return HTTP 204 No Content
     */
    @PatchMapping("/{id}/withdrawn")
    public ResponseEntity<Void> updateWithdrawn(@PathVariable Long id, @RequestParam boolean withdrawn) {
        ipoService.updateWithdrawn(id, withdrawn);
        return ResponseEntity.noContent().build();
    }

    /**
     * 월별 수익 집계
     *
     * <p>매도 완료(soldDate 존재)된 건만 집계합니다.
     * 미매도 건은 집계에서 제외되며, 매도 완료일({@code sold_date})의 월을 기준으로 수익을 합산합니다.</p>
     *
     * <p>수익 계산식: (매도가 - 공모가) × 배정수량 - 세금/수수료 - 청약수수료</p>
     *
     * @param year 조회할 연도 (필수)
     * @return 1~12월 수익 합계 배열 (미매도 월은 0원으로 포함)
     */
    @GetMapping("/monthly")
    public List<MonthlySummaryDto> monthly(@RequestParam int year) {
        return ipoService.getMonthlySummary(year);
    }

    /**
     * 자동완성용 종목 목록 조회
     *
     * <p>{@code ipo_stock} 테이블에 저장된 모든 종목의 종목명과 확정공모가를 반환합니다.
     * 프론트엔드의 종목명 입력 자동완성 및 공모가 자동채움에 사용됩니다.</p>
     *
     * @return 종목 목록 ({@link IpoStock} 리스트, 종목명 + 확정공모가 포함)
     */
    @GetMapping("/stocks")
    public List<IpoStock> stocks() {
        return ipoService.getAllStocks();
    }

    /**
     * 종목 수동 등록
     *
     * <p>자동완성 목록에 종목을 직접 추가합니다.
     * 청약 내역 등록 없이 종목만 별도로 등록하고 싶을 때 사용합니다.</p>
     *
     * @param stock 등록할 종목 정보 (종목명, 확정공모가 포함)
     * @return 저장된 종목 정보
     */
    @PostMapping("/stocks")
    public IpoStock createStock(@RequestBody IpoStock stock) {
        return ipoService.createStock(stock);
    }
}
