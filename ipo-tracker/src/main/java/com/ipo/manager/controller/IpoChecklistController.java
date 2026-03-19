package com.ipo.manager.controller;

import com.ipo.manager.domain.IpoChecklist;
import com.ipo.manager.repository.IpoChecklistRepository;
import com.ipo.manager.service.IpoStockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 공모주 청약 체크리스트 REST API 컨트롤러
 *
 * <p>공모주 청약 진행 상황을 계좌별로 추적하는 체크리스트 기능을 제공합니다.
 * 경록/지선/하준/하민 등 가족 계좌별 신청/배정/환불 상태를 관리합니다.</p>
 *
 * <p>프론트엔드에서 kokstock 일정과 병합하여 체크리스트 화면을 구성합니다.
 * 종목명({@code corpName})을 고유 식별자로 사용하여 upsert 방식으로 동작합니다.</p>
 *
 * <p>Base URL: /api/checklist</p>
 *
 * <pre>
 *   GET    /api/checklist          - 전체 체크리스트 조회
 *   PUT    /api/checklist          - 항목 저장(upsert: 없으면 INSERT, 있으면 UPDATE)
 *   DELETE /api/checklist/{name}   - 항목 삭제
 * </pre>
 */
@RestController
@RequestMapping("/api/checklist")
public class IpoChecklistController {

    /** 체크리스트 데이터에 접근하는 MyBatis 리포지토리 */
    private final IpoChecklistRepository repo;

    /** kokstock 일정 조회 및 캐시 관리 서비스 */
    private final IpoStockService        ipoStockService;

    /**
     * IpoChecklistController 생성자
     *
     * @param repo           체크리스트 리포지토리 (Spring DI로 주입)
     * @param ipoStockService IPO 종목 서비스 (Spring DI로 주입)
     */
    public IpoChecklistController(IpoChecklistRepository repo, IpoStockService ipoStockService) {
        this.repo           = repo;
        this.ipoStockService = ipoStockService;
    }

    /**
     * 저장된 체크리스트 전체 반환
     *
     * <p>DB에 저장된 모든 체크리스트 항목을 반환합니다.
     * 프론트엔드에서 이 데이터를 kokstock 일정과 병합하여 화면을 구성합니다.</p>
     *
     * @return HTTP 200 OK와 함께 전체 체크리스트 목록 ({@link IpoChecklist} 리스트)
     */
    @GetMapping
    public ResponseEntity<List<IpoChecklist>> getAll() {
        return ResponseEntity.ok(repo.findAll());
    }

    /**
     * 체크리스트 항목 저장 (upsert)
     *
     * <p>종목명({@code corpName})을 기준으로 이미 존재하면 UPDATE, 없으면 INSERT합니다.
     * 계좌별 청약 신청/배정/환불 상태 변경 시 호출됩니다.</p>
     *
     * @param body 저장할 체크리스트 데이터 (Request Body, corpName 필수)
     * @return HTTP 200 OK와 함께 저장된 체크리스트 항목 (최신 상태)
     */
    @PutMapping
    public ResponseEntity<IpoChecklist> upsert(@RequestBody IpoChecklist body) {
        repo.upsert(body);
        return ResponseEntity.ok(repo.findByCorpName(body.getCorpName()));
    }

    /**
     * 체크리스트 항목 삭제
     *
     * <p>종목명({@code corpName})으로 해당 체크리스트 항목을 DB에서 삭제합니다.
     * 청약이 완료되거나 취소된 종목을 체크리스트에서 제거할 때 사용합니다.</p>
     *
     * @param corpName 삭제할 종목명 (Path Variable, URL 인코딩 필요)
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{corpName}")
    public ResponseEntity<Void> delete(@PathVariable String corpName) {
        repo.deleteByCorpName(corpName);
        return ResponseEntity.noContent().build();
    }
}
