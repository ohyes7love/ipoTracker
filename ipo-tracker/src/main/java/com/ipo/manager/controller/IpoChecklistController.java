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
 * 계좌별(경록/지선/하준/하민) 신청/배정/환불 상태를 관리합니다.
 * 프론트엔드에서 kokstock 일정과 병합하여 체크리스트 화면을 구성합니다.
 *
 * API:
 *   GET  /api/checklist          - 전체 체크리스트 조회
 *   PUT  /api/checklist          - 항목 저장(upsert)
 *   DELETE /api/checklist/{name} - 항목 삭제
 */
@RestController
@RequestMapping("/api/checklist")
public class IpoChecklistController {

    private final IpoChecklistRepository repo;
    private final IpoStockService        ipoStockService;

    public IpoChecklistController(IpoChecklistRepository repo, IpoStockService ipoStockService) {
        this.repo           = repo;
        this.ipoStockService = ipoStockService;
    }

    /** 저장된 체크리스트 전체 반환 */
    @GetMapping
    public ResponseEntity<List<IpoChecklist>> getAll() {
        return ResponseEntity.ok(repo.findAll());
    }

    /** 체크리스트 항목 저장 (없으면 INSERT, 있으면 UPDATE) */
    @PutMapping
    public ResponseEntity<IpoChecklist> upsert(@RequestBody IpoChecklist body) {
        repo.upsert(body);
        return ResponseEntity.ok(repo.findByCorpName(body.getCorpName()));
    }

    /** 체크리스트 항목 삭제 */
    @DeleteMapping("/{corpName}")
    public ResponseEntity<Void> delete(@PathVariable String corpName) {
        repo.deleteByCorpName(corpName);
        return ResponseEntity.noContent().build();
    }
}
