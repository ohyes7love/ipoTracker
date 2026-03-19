package com.ipo.manager.service;

import com.ipo.manager.domain.IpoStock;
import com.ipo.manager.domain.IpoSubscription;
import com.ipo.manager.dto.IpoDto;
import com.ipo.manager.dto.MonthlySummaryDto;
import com.ipo.manager.repository.IpoStockRepository;
import com.ipo.manager.repository.IpoSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * IpoService - 공모주 청약 내역 비즈니스 로직 서비스
 *
 * <p>DB에 저장된 청약 내역({@link IpoSubscription})과 종목 정보({@link IpoStock})를 관리합니다.
 * 수익 계산은 {@link IpoSubscription} 엔티티의 {@code getProfit()} 메서드에서 처리됩니다.</p>
 *
 * <p>수익 계산식:</p>
 * <pre>
 *   수익 = (매도가 - 공모가) × 배정수량 - 세금/수수료 - 청약수수료
 * </pre>
 *
 * <p>클래스 레벨에 {@code @Transactional}이 선언되어 모든 메서드가 기본적으로
 * 트랜잭션 내에서 실행됩니다.</p>
 */
@Service
@Transactional
public class IpoService {

    /** 공모주 청약 내역 DB 접근 리포지토리 */
    private final IpoSubscriptionRepository repository;

    /** 종목 정보 DB 접근 리포지토리 (자동완성 및 공모가 동기화용) */
    private final IpoStockRepository stockRepository;

    /**
     * IpoService 생성자
     *
     * @param repository      청약 내역 리포지토리 (Spring DI로 주입)
     * @param stockRepository 종목 리포지토리 (Spring DI로 주입)
     */
    public IpoService(IpoSubscriptionRepository repository, IpoStockRepository stockRepository) {
        this.repository = repository;
        this.stockRepository = stockRepository;
    }

    /**
     * 전체 청약 내역 조회 (연도 무관)
     *
     * <p>DB에 저장된 모든 청약 내역을 ID 내림차순으로 반환합니다.</p>
     *
     * @return 전체 청약 내역 {@link IpoDto} 목록
     */
    public List<IpoDto> getAll() {
        return repository.findAll()
                .stream().map(IpoDto::from).collect(Collectors.toList());
    }

    /**
     * 연도별 청약 내역 조회
     *
     * <p>{@code subscription_year} 컬럼 기준으로 특정 연도의 청약 내역만 필터링합니다.
     * 결과는 ID 내림차순으로 정렬됩니다.</p>
     *
     * @param year 조회할 연도 (예: 2026)
     * @return 해당 연도의 청약 내역 {@link IpoDto} 목록 (상장일 기준 정렬)
     */
    public List<IpoDto> getByYear(int year) {
        return repository.findByYear(year)
                .stream().map(IpoDto::from).collect(Collectors.toList());
    }

    /**
     * 청약 내역 등록
     *
     * <p>새로운 공모주 청약 내역을 {@code ipo_subscription} 테이블에 저장합니다.
     * 저장 후 {@link #ensureIpoStock(IpoDto)}를 호출하여 {@code ipo_stock} 테이블에
     * 종목명 + 확정공모가를 upsert합니다 (자동완성 및 공모가 자동채움에 활용).</p>
     *
     * @param dto 등록할 청약 내역 데이터
     * @return 저장된 청약 내역 (생성된 ID 포함)
     */
    public IpoDto create(IpoDto dto) {
        IpoSubscription entity = dto.toEntity();
        repository.insert(entity);
        // 종목 정보도 함께 동기화 (자동완성용 ipo_stock 테이블 갱신)
        ensureIpoStock(dto);
        return IpoDto.from(entity);
    }

    /**
     * 청약 내역 수정
     *
     * <p>지정한 ID의 청약 내역을 수정합니다. null 안전 처리:</p>
     * <ul>
     *   <li>{@code soldQty}가 null이면 0으로 설정</li>
     *   <li>{@code taxAndFee}가 null이면 0L로 설정</li>
     *   <li>{@code subscriptionFee}가 null이면 0L로 설정</li>
     *   <li>{@code year}가 null이면 기존 값 유지</li>
     * </ul>
     * <p>수정 후 {@link #ensureIpoStock(IpoDto)}를 호출하여 종목 정보도 동기화합니다.</p>
     *
     * @param id  수정할 청약 내역의 고유 ID
     * @param dto 수정할 데이터가 담긴 DTO
     * @return 수정된 청약 내역 DTO
     * @throws RuntimeException 해당 ID의 레코드가 DB에 존재하지 않는 경우
     */
    public IpoDto update(Long id, IpoDto dto) {
        // DB에서 기존 엔티티 조회 (없으면 예외 발생)
        IpoSubscription entity = repository.findById(id);
        if (entity == null) throw new RuntimeException("Not found: " + id);
        // 각 필드 업데이트 (null 안전 처리 포함)
        entity.setStockName(dto.getStockName());
        entity.setBroker(dto.getBroker());
        entity.setOfferingPrice(dto.getOfferingPrice());
        entity.setSoldQty(dto.getSoldQty() != null ? dto.getSoldQty() : 0);
        entity.setSoldDate(dto.getSoldDate());
        entity.setSoldPrice(dto.getSoldPrice());
        entity.setTaxAndFee(dto.getTaxAndFee() != null ? dto.getTaxAndFee() : 0L);
        entity.setSubscriptionFee(dto.getSubscriptionFee() != null ? dto.getSubscriptionFee() : 0L);
        // year가 null이면 기존 연도 값 유지
        entity.setYear(dto.getYear() != null ? dto.getYear() : entity.getYear());
        entity.setSubscriptionStartDate(dto.getSubscriptionStartDate());
        entity.setSubscriptionEndDate(dto.getSubscriptionEndDate());
        entity.setListingDate(dto.getListingDate());
        repository.update(entity);
        // 종목 정보 동기화 (공모가 변경 시 ipo_stock 테이블도 갱신)
        ensureIpoStock(dto);
        return IpoDto.from(entity);
    }

    /**
     * 청약 내역 삭제
     *
     * <p>지정한 ID의 청약 내역을 {@code ipo_subscription} 테이블에서 삭제합니다.</p>
     *
     * @param id 삭제할 청약 내역의 고유 ID
     */
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /**
     * 출금완료 여부 업데이트
     *
     * <p>{@code withdrawn} 필드만 부분 업데이트합니다.
     * 전체 UPDATE를 피하고 단일 컬럼만 변경하여 성능을 최적화합니다.</p>
     *
     * @param id        업데이트할 청약 내역의 고유 ID
     * @param withdrawn 출금완료 여부 (true: 출금완료, false: 미출금)
     */
    public void updateWithdrawn(Long id, boolean withdrawn) {
        repository.updateWithdrawn(id, withdrawn);
    }

    /**
     * 월별 수익 집계
     *
     * <p>매도 완료({@code sold_date IS NOT NULL})된 건만 집계합니다.
     * 미매도 건은 제외되며, {@code sold_date}의 월을 기준으로 수익을 합산합니다.</p>
     *
     * <p>처리 흐름:</p>
     * <ol>
     *   <li>해당 연도의 매도 완료 청약 내역 조회</li>
     *   <li>수익이 null인 항목 필터링 (미매도 방어 처리)</li>
     *   <li>매도 월({@code sold_date.monthValue})을 키로 수익 합산</li>
     *   <li>1~12월 전체를 순회하며 {@link MonthlySummaryDto} 목록 구성 (없는 월은 0원)</li>
     * </ol>
     *
     * @param year 조회할 연도
     * @return 1~12월 수익 합계 목록 ({@link MonthlySummaryDto} 12개, 미매도 월은 0원)
     */
    public List<MonthlySummaryDto> getMonthlySummary(int year) {
        // 해당 연도의 매도 완료 내역만 조회
        List<IpoSubscription> soldList = repository.findByYearWithSoldDate(year);

        // 매도 월을 키로 수익을 합산하는 Map 생성
        // (profit이 null인 항목은 방어적 필터링으로 제외)
        Map<Integer, Long> monthlyMap = soldList.stream()
                .filter(e -> e.getProfit() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getSoldDate().getMonthValue(),   // 매도 월(1~12)을 키로 사용
                        Collectors.summingLong(IpoSubscription::getProfit)  // 월별 수익 합산
                ));

        // 1~12월 전체를 포함하는 결과 리스트 구성 (데이터 없는 월은 0원으로 채움)
        List<MonthlySummaryDto> result = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            result.add(new MonthlySummaryDto(m, monthlyMap.getOrDefault(m, 0L)));
        }
        return result;
    }

    /**
     * 종목명 자동완성용 전체 종목 목록 조회
     *
     * <p>{@code ipo_stock} 테이블의 모든 종목(종목명 + 확정공모가)을 반환합니다.</p>
     *
     * @return 전체 종목 목록 ({@link IpoStock} 리스트)
     */
    public List<IpoStock> getAllStocks() {
        return stockRepository.findAll();
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
    public IpoStock createStock(IpoStock stock) {
        stockRepository.insert(stock);
        return stock;
    }

    /**
     * 청약 내역 저장/수정 시 {@code ipo_stock} 테이블 동기화 (내부 헬퍼 메서드)
     *
     * <p>청약 내역이 등록되거나 수정될 때 자동완성용 종목 테이블을 최신 상태로 유지합니다.</p>
     *
     * <p>처리 로직:</p>
     * <ul>
     *   <li>종목명이 없거나 공백인 경우: 동기화 생략</li>
     *   <li>종목이 이미 존재하고 공모가가 있는 경우: 공모가만 갱신(UPDATE)</li>
     *   <li>종목이 존재하지 않는 경우: 신규 등록(INSERT)</li>
     * </ul>
     *
     * @param dto 저장/수정된 청약 내역 DTO (종목명과 공모가 참조)
     */
    private void ensureIpoStock(IpoDto dto) {
        // 종목명이 없으면 동기화 불필요
        if (dto.getStockName() == null || dto.getStockName().isBlank()) return;

        IpoStock existing = stockRepository.findByStockName(dto.getStockName());
        if (existing != null) {
            // 이미 존재하는 종목이고 공모가 정보가 있으면 공모가만 업데이트
            if (dto.getOfferingPrice() != null) {
                existing.setOfferingPrice(dto.getOfferingPrice());
                stockRepository.update(existing);
            }
        } else {
            // 존재하지 않는 종목이면 새로 등록
            IpoStock stock = new IpoStock();
            stock.setStockName(dto.getStockName());
            stock.setOfferingPrice(dto.getOfferingPrice());
            stockRepository.insert(stock);
        }
    }
}
