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
