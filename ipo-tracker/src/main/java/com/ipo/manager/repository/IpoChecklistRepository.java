package com.ipo.manager.repository;

import com.ipo.manager.domain.IpoChecklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 공모주 청약 체크리스트 MyBatis 매퍼
 *
 * ipo_checklist 테이블에 대한 CRUD 연산을 제공합니다.
 * corp_name을 PK(종목명)로 사용하며 INSERT OR UPDATE(upsert) 방식으로 저장합니다.
 */
@Mapper
public interface IpoChecklistRepository {

    /** 전체 체크리스트 항목 조회 */
    List<IpoChecklist> findAll();

    /** 종목명으로 단일 항목 조회 */
    IpoChecklist findByCorpName(@Param("corpName") String corpName);

    /** 항목 저장 (없으면 INSERT, 있으면 UPDATE) */
    void upsert(IpoChecklist entity);

    /** 항목 삭제 */
    void deleteByCorpName(@Param("corpName") String corpName);
}
