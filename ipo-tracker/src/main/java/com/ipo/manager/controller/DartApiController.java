package com.ipo.manager.controller;

import com.ipo.manager.service.DartApiService;
import com.ipo.manager.service.IpoStockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * DART(전자공시시스템) 및 kokstock 연동 REST API 컨트롤러
 *
 * <p>공모주 일정 스크래핑(kokstock), DART 공시 조회, 캘린더 데이터 제공,
 * 증권사별 통계 집계 등의 기능을 담당합니다.</p>
 *
 * <p>Base URL: /api/dart</p>
 *
 * <pre>
 *   GET  /api/dart/search         - 종목명 자동완성 (kokstock 일정 객체 반환)
 *   GET  /api/dart/ipo-detail     - 종목 상세 정보 (kokstock 팝업 데이터)
 *   POST /api/dart/refresh        - 캐시 강제 갱신
 *   GET  /api/dart/calendar       - 캘린더용 월별 공모주 일정
 *   GET  /api/dart/broker-stats   - 증권사별 공모 참여 건수 집계
 *   GET  /api/dart/debug          - 디버그용 list.json 원본 데이터
 *   GET  /api/dart/ipos           - DART API를 통한 최근 공모 목록 조회
 * </pre>
 */
@RestController
@RequestMapping("/api/dart")
public class DartApiController {

    /** DART 전자공시 API 연동 서비스 */
    private final DartApiService dartApiService;

    /** kokstock 스크래핑 및 캐시 관리 서비스 */
    private final IpoStockService ipoStockService;

    /**
     * DartApiController 생성자
     *
     * @param dartApiService  DART API 서비스 (Spring DI로 주입)
     * @param ipoStockService IPO 종목 스크래핑 서비스 (Spring DI로 주입)
     */
    public DartApiController(DartApiService dartApiService, IpoStockService ipoStockService) {
        this.dartApiService = dartApiService;
        this.ipoStockService = ipoStockService;
    }

    /**
     * 종목명 자동완성 검색
     *
     * <p>검색어({@code q})로 kokstock 캐시 데이터를 조회하여 일치하는 공모주 일정 객체를 반환합니다.
     * 반환된 데이터는 프론트엔드에서 청약 내역 입력 폼의 날짜 자동채움에 활용됩니다.</p>
     *
     * @param q 검색할 종목명 키워드 (부분 일치 검색)
     * @return HTTP 200 OK와 함께 매칭된 공모주 일정 정보 (kokstock 형식)
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q) {
        return ResponseEntity.ok(ipoStockService.searchSchedule(q));
    }

    /**
     * 종목 상세 정보 조회
     *
     * <p>kokstock의 고유 인덱스({@code idx})로 특정 공모주의 상세 정보를 반환합니다.
     * 프론트엔드 팝업(모달)에서 청약 기간, 상장일, 희망공모가 등 상세 데이터를 표시하는 데 사용됩니다.</p>
     *
     * @param idx kokstock에서 사용하는 공모주 고유 인덱스
     * @return HTTP 200 OK와 함께 공모주 상세 정보 (kokstock 팝업 데이터 형식)
     */
    @GetMapping("/ipo-detail")
    public ResponseEntity<?> ipoDetail(@RequestParam String idx) {
        return ResponseEntity.ok(ipoStockService.getIpoDetail(idx));
    }

    /**
     * kokstock 캐시 강제 갱신
     *
     * <p>서버에 저장된 kokstock 스크래핑 캐시를 즉시 갱신합니다.
     * 최신 공모주 일정이 반영되지 않을 때 수동으로 호출합니다.</p>
     *
     * <p>응답 형식:</p>
     * <ul>
     *   <li>성공: {@code {"status": "ok", "count": 갱신된_건수}}</li>
     *   <li>실패: {@code {"status": "error", "message": "오류메시지"}}</li>
     * </ul>
     *
     * @return HTTP 200 OK (성공 시) 또는 HTTP 500 Internal Server Error (실패 시)
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh() {
        String result = ipoStockService.refreshCache();
        // 반환 형식: "ok:건수" (성공) 또는 오류 메시지 문자열 (실패)
        boolean ok = result.startsWith("ok:");
        int count = ok ? Integer.parseInt(result.split(":")[1]) : 0;
        return ok
            ? ResponseEntity.ok(java.util.Map.of("status", "ok", "count", count))
            : ResponseEntity.internalServerError().body(java.util.Map.of("status", "error", "message", result));
    }

    /**
     * 캘린더용 월별 공모주 일정 조회
     *
     * <p>프론트엔드 캘린더 화면에 표시할 특정 연월의 공모주 일정을 반환합니다.
     * kokstock 스크래핑 데이터를 기반으로 청약 기간, 상장일 등을 포함합니다.</p>
     *
     * @param year  조회할 연도 (예: 2026)
     * @param month 조회할 월 (1~12)
     * @return HTTP 200 OK와 함께 해당 월의 공모주 일정 목록
     */
    @GetMapping("/calendar")
    public ResponseEntity<?> calendar(@RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(ipoStockService.getSchedulesByMonth(year, month));
    }

    /**
     * 증권사별 공모 참여 건수 집계
     *
     * <p>특정 연월에 청약 가능한 모든 종목의 상세 정보를 조회하여
     * 증권사(주관사)별로 공모 참여 건수를 집계합니다.
     * 청약 계좌를 개설할 증권사 선택 시 참고 데이터로 활용됩니다.</p>
     *
     * @param year  조회할 연도 (예: 2026)
     * @param month 조회할 월 (1~12)
     * @return HTTP 200 OK와 함께 증권사별 참여 건수 집계 결과
     */
    @GetMapping("/broker-stats")
    public ResponseEntity<?> brokerStats(@RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(ipoStockService.getBrokerStats(year, month));
    }

    /**
     * 디버그용 list.json 원본 데이터 조회
     *
     * <p>DART API에서 받아온 list.json 원본 데이터를 반환합니다.
     * {@code report_nm} 필드 확인 등 개발/디버깅 목적으로 사용됩니다.</p>
     *
     * @return HTTP 200 OK와 함께 DART API list.json 원본 데이터
     */
    @GetMapping("/debug")
    public ResponseEntity<?> debug() {
        return ResponseEntity.ok(dartApiService.debugRawFilings());
    }

    /**
     * DART API를 통한 최근 공모주 목록 조회
     *
     * <p>DART 전자공시 API를 직접 호출하여 최근 공모주 공시 목록을 가져옵니다.
     * {@code application.properties}에 {@code dart.api.key}가 설정되어 있어야 합니다.</p>
     *
     * <p>응답 형식:</p>
     * <ul>
     *   <li>API 키 미설정: HTTP 400 Bad Request + 오류 메시지</li>
     *   <li>성공: HTTP 200 OK + 공모주 목록</li>
     *   <li>API 호출 실패: HTTP 500 Internal Server Error + 오류 메시지</li>
     * </ul>
     *
     * @return HTTP 200 OK (성공), HTTP 400 (API 키 미설정), HTTP 500 (서버 오류)
     */
    @GetMapping("/ipos")
    public ResponseEntity<?> getIpos() {
        // DART API 키 설정 여부 확인
        if (!dartApiService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "DART API 키가 설정되지 않았습니다. application.properties에 dart.api.key를 설정해주세요."));
        }
        try {
            return ResponseEntity.ok(dartApiService.getRecentIpoList());
        } catch (Exception e) {
            // API 호출 중 예외 발생 시 500 응답 반환
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
