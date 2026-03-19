package com.ipo.manager.domain;

import java.time.LocalDate;

/**
 * 공모주 청약 내역 도메인 엔티티
 *
 * <p>{@code ipo_subscription} 테이블과 매핑되는 도메인 객체입니다.
 * 한 건의 공모주 청약 내역을 표현하며, 수익 및 수익률을 계산하는 비즈니스 로직을 포함합니다.</p>
 *
 * <p>수익 계산식:</p>
 * <pre>
 *   수익 = (매도가 - 공모가) × 배정수량 - 세금/수수료 - 청약수수료
 *   수익률 = 수익 / (공모가 × 배정수량) × 100 (%)
 * </pre>
 *
 * <p>미매도 상태({@code soldPrice == null})인 경우 수익 및 수익률은 {@code null}을 반환합니다.</p>
 */
public class IpoSubscription {

    /** 청약 내역 고유 ID (DB AUTO_INCREMENT) */
    private Long id;

    /** 청약한 종목명 (예: "삼성바이오로직스") */
    private String stockName;

    /** 청약 증권사명 (예: "미래에셋증권") */
    private String broker;

    /** 확정 공모가 (원 단위) */
    private Long offeringPrice;

    /** 배정된 수량 (주) */
    private Integer soldQty;

    /** 매도 완료 날짜 (미매도 시 null) */
    private LocalDate soldDate;

    /** 실제 매도 가격 (원 단위, 미매도 시 null) */
    private Long soldPrice;

    /** 매도 시 발생한 세금 및 수수료 합계 (원 단위, 기본값 0) */
    private Long taxAndFee = 0L;

    /** 청약 시 납부한 청약수수료 (원 단위, 기본값 0) */
    private Long subscriptionFee = 0L;

    /** 청약 연도 (상장일 기준, {@code subscription_year} 컬럼에 저장) */
    private Integer year;

    /** 청약 시작일 */
    private LocalDate subscriptionStartDate;

    /** 청약 마감일 */
    private LocalDate subscriptionEndDate;

    /** 상장일 (시장 상장 예정일 또는 실제 상장일) */
    private LocalDate listingDate;

    /** 참여 계좌 목록 (쉼표 구분, 예: "경록,지선") */
    private String accounts;

    /** 출금완료 여부 (true: 수익금 출금 완료, false: 미출금) */
    private boolean withdrawn;

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
     * 매도 완료 날짜를 반환합니다.
     *
     * @return 매도 날짜 (미매도 시 null)
     */
    public LocalDate getSoldDate() { return soldDate; }

    /**
     * 매도 완료 날짜를 설정합니다.
     *
     * @param soldDate 설정할 매도 날짜 (미매도 시 null)
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
     * @return 세금/수수료 합계 (원 단위, 기본값 0)
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
     * @return 청약수수료 (원 단위, 기본값 0)
     */
    public Long getSubscriptionFee() { return subscriptionFee; }

    /**
     * 청약수수료를 설정합니다.
     *
     * @param subscriptionFee 설정할 청약수수료 (원 단위)
     */
    public void setSubscriptionFee(Long subscriptionFee) { this.subscriptionFee = subscriptionFee; }

    /**
     * 청약 연도를 반환합니다.
     *
     * @return 청약 연도 (상장일 기준)
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
     * @return 참여 계좌 목록 (쉼표 구분, 예: "경록,지선")
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

    /**
     * 청약 수익을 계산하여 반환합니다.
     *
     * <p>수익 계산식:</p>
     * <pre>
     *   수익 = (매도가 - 공모가) × 배정수량 - 세금/수수료 - 청약수수료
     * </pre>
     *
     * <p>{@code soldPrice}가 null인 경우(미매도 상태) {@code null}을 반환합니다.
     * {@code soldQty}, {@code taxAndFee}, {@code subscriptionFee}가 null이면 0으로 처리합니다.</p>
     *
     * @return 계산된 수익 (원 단위), 미매도 시 null
     */
    public Long getProfit() {
        // 매도가가 없으면 수익 계산 불가 (미매도 상태)
        if (soldPrice == null) return null;
        long qty = soldQty != null ? soldQty : 0;
        long fee = taxAndFee != null ? taxAndFee : 0L;
        long subFee = subscriptionFee != null ? subscriptionFee : 0L;
        // (매도가 - 공모가) × 수량 - 세금/수수료 - 청약수수료
        return (soldPrice - offeringPrice) * qty - fee - subFee;
    }

    /**
     * 청약 수익률을 계산하여 반환합니다.
     *
     * <p>수익률 계산식:</p>
     * <pre>
     *   수익률(%) = 수익 / (공모가 × 배정수량) × 100
     * </pre>
     *
     * <p>다음 경우에는 {@code null}을 반환합니다:</p>
     * <ul>
     *   <li>미매도 상태 ({@code getProfit()} == null)</li>
     *   <li>배정수량이 0인 경우</li>
     *   <li>공모가가 null이거나 0인 경우 (0으로 나누기 방지)</li>
     * </ul>
     *
     * @return 수익률 (% 단위, 소수점 포함), 계산 불가 시 null
     */
    public Double getProfitRate() {
        Long profit = getProfit();
        // 수익이 null이면 수익률도 null (미매도 상태)
        if (profit == null) return null;
        long qty = soldQty != null ? soldQty : 0;
        // 배정수량 또는 공모가가 0이면 0으로 나누기 방지
        if (qty == 0 || offeringPrice == null || offeringPrice == 0) return null;
        return (double) profit / (offeringPrice * qty) * 100.0;
    }
}
