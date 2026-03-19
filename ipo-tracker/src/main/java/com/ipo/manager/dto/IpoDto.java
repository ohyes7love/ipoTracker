package com.ipo.manager.dto;

import com.ipo.manager.domain.IpoSubscription;
import java.time.LocalDate;

/**
 * 공모주 청약 내역 데이터 전송 객체 (DTO)
 *
 * <p>컨트롤러와 서비스 계층 간 데이터 전달, 그리고 REST API의 요청/응답 바디로 사용됩니다.
 * {@link IpoSubscription} 도메인 엔티티와 1:1로 대응하며, 추가로 계산된 수익({@code profit})과
 * 수익률({@code profitRate}) 필드를 포함합니다.</p>
 *
 * <p>주요 변환 메서드:</p>
 * <ul>
 *   <li>{@link #from(IpoSubscription)} - 엔티티 → DTO 변환 (정적 팩토리 메서드)</li>
 *   <li>{@link #toEntity()} - DTO → 엔티티 변환</li>
 * </ul>
 */
public class IpoDto {

    /** 청약 내역 고유 ID */
    private Long id;

    /** 청약 종목명 */
    private String stockName;

    /** 청약 증권사명 */
    private String broker;

    /** 확정 공모가 (원 단위) */
    private Long offeringPrice;

    /** 배정 수량 (주) */
    private Integer soldQty;

    /** 매도 완료 날짜 (미매도 시 null) */
    private LocalDate soldDate;

    /** 실제 매도 가격 (원 단위, 미매도 시 null) */
    private Long soldPrice;

    /** 세금 및 수수료 합계 (원 단위) */
    private Long taxAndFee;

    /** 청약수수료 (원 단위) */
    private Long subscriptionFee;

    /**
     * 계산된 수익 (원 단위)
     *
     * <p>엔티티의 {@link IpoSubscription#getProfit()} 메서드로 계산됩니다.
     * 미매도 상태이면 null입니다.</p>
     */
    private Long profit;

    /**
     * 계산된 수익률 (% 단위)
     *
     * <p>엔티티의 {@link IpoSubscription#getProfitRate()} 메서드로 계산됩니다.
     * 미매도 상태이거나 계산 불가 시 null입니다.</p>
     */
    private Double profitRate;

    /** 청약 연도 */
    private Integer year;

    /** 청약 시작일 */
    private LocalDate subscriptionStartDate;

    /** 청약 마감일 */
    private LocalDate subscriptionEndDate;

    /** 상장일 */
    private LocalDate listingDate;

    /** 참여 계좌 목록 (쉼표 구분, 예: "경록,지선") */
    private String accounts;

    /** 출금완료 여부 */
    private boolean withdrawn;

    /**
     * {@link IpoSubscription} 엔티티를 {@link IpoDto}로 변환하는 정적 팩토리 메서드
     *
     * <p>엔티티의 모든 필드를 DTO에 복사하며, 수익과 수익률은 엔티티의
     * {@link IpoSubscription#getProfit()} 및 {@link IpoSubscription#getProfitRate()}를
     * 호출하여 계산된 값을 설정합니다.</p>
     *
     * @param entity 변환할 {@link IpoSubscription} 엔티티 (not null)
     * @return 변환된 {@link IpoDto} 객체
     */
    public static IpoDto from(IpoSubscription entity) {
        IpoDto dto = new IpoDto();
        dto.id = entity.getId();
        dto.stockName = entity.getStockName();
        dto.broker = entity.getBroker();
        dto.offeringPrice = entity.getOfferingPrice();
        dto.soldQty = entity.getSoldQty();
        dto.soldDate = entity.getSoldDate();
        dto.soldPrice = entity.getSoldPrice();
        dto.taxAndFee = entity.getTaxAndFee();
        dto.subscriptionFee = entity.getSubscriptionFee();
        // 수익 및 수익률은 엔티티의 비즈니스 로직으로 계산
        dto.profit = entity.getProfit();
        dto.profitRate = entity.getProfitRate();
        dto.year = entity.getYear();
        dto.subscriptionStartDate = entity.getSubscriptionStartDate();
        dto.subscriptionEndDate   = entity.getSubscriptionEndDate();
        dto.listingDate           = entity.getListingDate();
        dto.accounts              = entity.getAccounts();
        dto.withdrawn             = entity.isWithdrawn();
        return dto;
    }

    /**
     * {@link IpoDto}를 {@link IpoSubscription} 엔티티로 변환합니다.
     *
     * <p>null 안전 처리:</p>
     * <ul>
     *   <li>{@code soldQty}가 null이면 0으로 설정</li>
     *   <li>{@code taxAndFee}가 null이면 0L로 설정</li>
     *   <li>{@code subscriptionFee}가 null이면 0L로 설정</li>
     * </ul>
     *
     * <p>수익({@code profit})과 수익률({@code profitRate})은 읽기 전용 계산 필드이므로
     * 엔티티로 복사하지 않습니다. (엔티티 메서드에서 동적으로 계산)</p>
     *
     * @return 변환된 {@link IpoSubscription} 엔티티 객체
     */
    public IpoSubscription toEntity() {
        IpoSubscription entity = new IpoSubscription();
        entity.setId(this.id);
        entity.setStockName(this.stockName);
        entity.setBroker(this.broker);
        entity.setOfferingPrice(this.offeringPrice);
        // null 안전 처리: soldQty가 null이면 0으로 기본값 설정
        entity.setSoldQty(this.soldQty != null ? this.soldQty : 0);
        entity.setSoldDate(this.soldDate);
        entity.setSoldPrice(this.soldPrice);
        // null 안전 처리: taxAndFee가 null이면 0L로 기본값 설정
        entity.setTaxAndFee(this.taxAndFee != null ? this.taxAndFee : 0L);
        // null 안전 처리: subscriptionFee가 null이면 0L로 기본값 설정
        entity.setSubscriptionFee(this.subscriptionFee != null ? this.subscriptionFee : 0L);
        entity.setYear(this.year);
        entity.setSubscriptionStartDate(this.subscriptionStartDate);
        entity.setSubscriptionEndDate(this.subscriptionEndDate);
        entity.setListingDate(this.listingDate);
        entity.setAccounts(this.accounts);
        entity.setWithdrawn(this.withdrawn);
        return entity;
    }

    /**
     * 고유 ID를 반환합니다.
     *
     * @return 청약 내역 고유 ID
     */
    public Long getId() { return id; }

    /**
     * 고유 ID를 설정합니다.
     *
     * @param id 설정할 고유 ID
     */
    public void setId(Long id) { this.id = id; }

    /**
     * 종목명을 반환합니다.
     *
     * @return 청약 종목명
     */
    public String getStockName() { return stockName; }

    /**
     * 종목명을 설정합니다.
     *
     * @param stockName 설정할 종목명
     */
    public void setStockName(String stockName) { this.stockName = stockName; }

    /**
     * 청약 증권사명을 반환합니다.
     *
     * @return 청약 증권사명
     */
    public String getBroker() { return broker; }

    /**
     * 청약 증권사명을 설정합니다.
     *
     * @param broker 설정할 증권사명
     */
    public void setBroker(String broker) { this.broker = broker; }

    /**
     * 확정 공모가를 반환합니다.
     *
     * @return 확정 공모가 (원 단위)
     */
    public Long getOfferingPrice() { return offeringPrice; }

    /**
     * 확정 공모가를 설정합니다.
     *
     * @param offeringPrice 설정할 확정 공모가 (원 단위)
     */
    public void setOfferingPrice(Long offeringPrice) { this.offeringPrice = offeringPrice; }

    /**
     * 배정 수량을 반환합니다.
     *
     * @return 배정 수량 (주)
     */
    public Integer getSoldQty() { return soldQty; }

    /**
     * 배정 수량을 설정합니다.
     *
     * @param soldQty 설정할 배정 수량 (주)
     */
    public void setSoldQty(Integer soldQty) { this.soldQty = soldQty; }

    /**
     * 매도 날짜를 반환합니다.
     *
     * @return 매도 날짜 (미매도 시 null)
     */
    public LocalDate getSoldDate() { return soldDate; }

    /**
     * 매도 날짜를 설정합니다.
     *
     * @param soldDate 설정할 매도 날짜
     */
    public void setSoldDate(LocalDate soldDate) { this.soldDate = soldDate; }

    /**
     * 매도 가격을 반환합니다.
     *
     * @return 매도 가격 (원 단위, 미매도 시 null)
     */
    public Long getSoldPrice() { return soldPrice; }

    /**
     * 매도 가격을 설정합니다.
     *
     * @param soldPrice 설정할 매도 가격 (원 단위)
     */
    public void setSoldPrice(Long soldPrice) { this.soldPrice = soldPrice; }

    /**
     * 세금 및 수수료 합계를 반환합니다.
     *
     * @return 세금/수수료 합계 (원 단위)
     */
    public Long getTaxAndFee() { return taxAndFee; }

    /**
     * 세금 및 수수료 합계를 설정합니다.
     *
     * @param taxAndFee 설정할 세금/수수료 합계 (원 단위)
     */
    public void setTaxAndFee(Long taxAndFee) { this.taxAndFee = taxAndFee; }

    /**
     * 청약수수료를 반환합니다.
     *
     * @return 청약수수료 (원 단위)
     */
    public Long getSubscriptionFee() { return subscriptionFee; }

    /**
     * 청약수수료를 설정합니다.
     *
     * @param subscriptionFee 설정할 청약수수료 (원 단위)
     */
    public void setSubscriptionFee(Long subscriptionFee) { this.subscriptionFee = subscriptionFee; }

    /**
     * 계산된 수익을 반환합니다.
     *
     * @return 수익 (원 단위, 미매도 시 null)
     */
    public Long getProfit() { return profit; }

    /**
     * 수익을 설정합니다.
     *
     * @param profit 설정할 수익 (원 단위)
     */
    public void setProfit(Long profit) { this.profit = profit; }

    /**
     * 계산된 수익률을 반환합니다.
     *
     * @return 수익률 (% 단위, 미매도 또는 계산 불가 시 null)
     */
    public Double getProfitRate() { return profitRate; }

    /**
     * 수익률을 설정합니다.
     *
     * @param profitRate 설정할 수익률 (% 단위)
     */
    public void setProfitRate(Double profitRate) { this.profitRate = profitRate; }

    /**
     * 청약 연도를 반환합니다.
     *
     * @return 청약 연도
     */
    public Integer getYear() { return year; }

    /**
     * 청약 연도를 설정합니다.
     *
     * @param year 설정할 청약 연도
     */
    public void setYear(Integer year) { this.year = year; }

    /**
     * 청약 시작일을 반환합니다.
     *
     * @return 청약 시작일
     */
    public LocalDate getSubscriptionStartDate() { return subscriptionStartDate; }

    /**
     * 청약 시작일을 설정합니다.
     *
     * @param subscriptionStartDate 설정할 청약 시작일
     */
    public void setSubscriptionStartDate(LocalDate subscriptionStartDate) { this.subscriptionStartDate = subscriptionStartDate; }

    /**
     * 청약 마감일을 반환합니다.
     *
     * @return 청약 마감일
     */
    public LocalDate getSubscriptionEndDate() { return subscriptionEndDate; }

    /**
     * 청약 마감일을 설정합니다.
     *
     * @param subscriptionEndDate 설정할 청약 마감일
     */
    public void setSubscriptionEndDate(LocalDate subscriptionEndDate) { this.subscriptionEndDate = subscriptionEndDate; }

    /**
     * 상장일을 반환합니다.
     *
     * @return 상장일
     */
    public LocalDate getListingDate() { return listingDate; }

    /**
     * 상장일을 설정합니다.
     *
     * @param listingDate 설정할 상장일
     */
    public void setListingDate(LocalDate listingDate) { this.listingDate = listingDate; }

    /**
     * 참여 계좌 목록을 반환합니다.
     *
     * @return 참여 계좌 목록 (쉼표 구분 문자열)
     */
    public String getAccounts() { return accounts; }

    /**
     * 참여 계좌 목록을 설정합니다.
     *
     * @param accounts 설정할 계좌 목록 (쉼표 구분 문자열)
     */
    public void setAccounts(String accounts) { this.accounts = accounts; }

    /**
     * 출금완료 여부를 반환합니다.
     *
     * @return 출금완료 여부 (true: 출금완료, false: 미출금)
     */
    public boolean isWithdrawn() { return withdrawn; }

    /**
     * 출금완료 여부를 설정합니다.
     *
     * @param withdrawn 설정할 출금완료 여부
     */
    public void setWithdrawn(boolean withdrawn) { this.withdrawn = withdrawn; }
}
