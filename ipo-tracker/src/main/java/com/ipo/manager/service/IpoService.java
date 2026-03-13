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
 * IpoService - 공모주 청약 내역 비즈니스 로직
 *
 * <p>DB에 저장된 청약 내역(IpoSubscription)과 종목 정보(IpoStock)를 관리합니다.
 * 수익 계산은 IpoSubscription 엔티티의 getProfit() 에서 처리됩니다.</p>
 *
 * <pre>
 *   수익 = (매도가 - 공모가) × 배정수량 - 세금/수수료 - 청약수수료
 * </pre>
 */
@Service
@Transactional
public class IpoService {

    private final IpoSubscriptionRepository repository;
    private final IpoStockRepository stockRepository;

    public IpoService(IpoSubscriptionRepository repository, IpoStockRepository stockRepository) {
        this.repository = repository;
        this.stockRepository = stockRepository;
    }

    /** 전체 청약 내역 조회 (연도 무관) */
    public List<IpoDto> getAll() {
        return repository.findAll()
                .stream().map(IpoDto::from).collect(Collectors.toList());
    }

    /**
     * 연도별 청약 내역 조회
     *
     * @param year 조회할 연도 (예: 2026)
     * @return 해당 연도의 청약 내역 목록 (상장일 기준 정렬)
     */
    public List<IpoDto> getByYear(int year) {
        return repository.findByYear(year)
                .stream().map(IpoDto::from).collect(Collectors.toList());
    }

    /**
     * 청약 내역 등록
     * <p>등록 시 ipo_stock 테이블에 종목명 + 확정공모가를 upsert합니다
     * (자동완성 및 공모가 자동채움에 활용).</p>
     */
    public IpoDto create(IpoDto dto) {
        IpoSubscription entity = dto.toEntity();
        repository.insert(entity);
        ensureIpoStock(dto);
        return IpoDto.from(entity);
    }

    /**
     * 청약 내역 수정
     *
     * @param id  수정할 레코드 ID
     * @param dto 수정할 데이터
     * @throws RuntimeException 해당 ID가 존재하지 않는 경우
     */
    public IpoDto update(Long id, IpoDto dto) {
        IpoSubscription entity = repository.findById(id);
        if (entity == null) throw new RuntimeException("Not found: " + id);
        entity.setStockName(dto.getStockName());
        entity.setBroker(dto.getBroker());
        entity.setOfferingPrice(dto.getOfferingPrice());
        entity.setSoldQty(dto.getSoldQty() != null ? dto.getSoldQty() : 0);
        entity.setSoldDate(dto.getSoldDate());
        entity.setSoldPrice(dto.getSoldPrice());
        entity.setTaxAndFee(dto.getTaxAndFee() != null ? dto.getTaxAndFee() : 0L);
        entity.setSubscriptionFee(dto.getSubscriptionFee() != null ? dto.getSubscriptionFee() : 0L);
        entity.setYear(dto.getYear() != null ? dto.getYear() : entity.getYear());
        entity.setSubscriptionStartDate(dto.getSubscriptionStartDate());
        entity.setSubscriptionEndDate(dto.getSubscriptionEndDate());
        entity.setListingDate(dto.getListingDate());
        repository.update(entity);
        ensureIpoStock(dto);
        return IpoDto.from(entity);
    }

    /** 청약 내역 삭제 */
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /**
     * 월별 수익 집계
     * <p>매도 완료(soldDate 존재)된 건만 집계하며, 미매도 건은 제외합니다.</p>
     *
     * @param year 조회 연도
     * @return 1~12월 전체 월별 수익 합계 (미매도 월은 0원)
     */
    public List<MonthlySummaryDto> getMonthlySummary(int year) {
        List<IpoSubscription> soldList = repository.findByYearWithSoldDate(year);
        Map<Integer, Long> monthlyMap = soldList.stream()
                .filter(e -> e.getProfit() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getSoldDate().getMonthValue(),
                        Collectors.summingLong(IpoSubscription::getProfit)
                ));
        List<MonthlySummaryDto> result = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            result.add(new MonthlySummaryDto(m, monthlyMap.getOrDefault(m, 0L)));
        }
        return result;
    }

    /** 종목명 자동완성용 전체 종목 목록 조회 */
    public List<IpoStock> getAllStocks() {
        return stockRepository.findAll();
    }

    /** 종목 수동 등록 */
    public IpoStock createStock(IpoStock stock) {
        stockRepository.insert(stock);
        return stock;
    }

    /**
     * 청약 내역 저장/수정 시 ipo_stock 테이블 동기화
     * <p>종목이 이미 존재하면 공모가만 갱신, 없으면 신규 등록합니다.</p>
     */
    private void ensureIpoStock(IpoDto dto) {
        if (dto.getStockName() == null || dto.getStockName().isBlank()) return;
        IpoStock existing = stockRepository.findByStockName(dto.getStockName());
        if (existing != null) {
            if (dto.getOfferingPrice() != null) {
                existing.setOfferingPrice(dto.getOfferingPrice());
                stockRepository.update(existing);
            }
        } else {
            IpoStock stock = new IpoStock();
            stock.setStockName(dto.getStockName());
            stock.setOfferingPrice(dto.getOfferingPrice());
            stockRepository.insert(stock);
        }
    }
}
