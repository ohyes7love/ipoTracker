package com.ipo.manager.domain;

import java.time.LocalDate;

public class IpoSubscription {

    private Long id;
    private String stockName;
    private String broker;
    private Long offeringPrice;
    private Integer soldQty;
    private LocalDate soldDate;
    private Long soldPrice;
    private Long taxAndFee = 0L;
    private Long subscriptionFee = 0L;
    private Integer year;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionEndDate;
    private LocalDate listingDate;
    private String accounts;  // 참여 계좌 목록 (쉼표 구분)
    private boolean withdrawn; // 출금완료 여부

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }

    public String getBroker() { return broker; }
    public void setBroker(String broker) { this.broker = broker; }

    public Long getOfferingPrice() { return offeringPrice; }
    public void setOfferingPrice(Long offeringPrice) { this.offeringPrice = offeringPrice; }

    public Integer getSoldQty() { return soldQty; }
    public void setSoldQty(Integer soldQty) { this.soldQty = soldQty; }

    public LocalDate getSoldDate() { return soldDate; }
    public void setSoldDate(LocalDate soldDate) { this.soldDate = soldDate; }

    public Long getSoldPrice() { return soldPrice; }
    public void setSoldPrice(Long soldPrice) { this.soldPrice = soldPrice; }

    public Long getTaxAndFee() { return taxAndFee; }
    public void setTaxAndFee(Long taxAndFee) { this.taxAndFee = taxAndFee; }

    public Long getSubscriptionFee() { return subscriptionFee; }
    public void setSubscriptionFee(Long subscriptionFee) { this.subscriptionFee = subscriptionFee; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public LocalDate getSubscriptionStartDate() { return subscriptionStartDate; }
    public void setSubscriptionStartDate(LocalDate subscriptionStartDate) { this.subscriptionStartDate = subscriptionStartDate; }

    public LocalDate getSubscriptionEndDate() { return subscriptionEndDate; }
    public void setSubscriptionEndDate(LocalDate subscriptionEndDate) { this.subscriptionEndDate = subscriptionEndDate; }

    public LocalDate getListingDate() { return listingDate; }
    public void setListingDate(LocalDate listingDate) { this.listingDate = listingDate; }

    /** 참여 계좌 목록 (쉼표 구분, 예: "경록,지선") */
    public String getAccounts() { return accounts; }
    public void setAccounts(String accounts) { this.accounts = accounts; }

    public boolean isWithdrawn() { return withdrawn; }
    public void setWithdrawn(boolean withdrawn) { this.withdrawn = withdrawn; }

    public Long getProfit() {
        if (soldPrice == null) return null;
        long qty = soldQty != null ? soldQty : 0;
        long fee = taxAndFee != null ? taxAndFee : 0L;
        long subFee = subscriptionFee != null ? subscriptionFee : 0L;
        return (soldPrice - offeringPrice) * qty - fee - subFee;
    }

    public Double getProfitRate() {
        Long profit = getProfit();
        if (profit == null) return null;
        long qty = soldQty != null ? soldQty : 0;
        if (qty == 0 || offeringPrice == null || offeringPrice == 0) return null;
        return (double) profit / (offeringPrice * qty) * 100.0;
    }
}
