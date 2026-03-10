package com.ipo.manager.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "IPO_SUBSCRIPTION")
public class IpoSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String stockName;

    @Column(nullable = false)
    private String broker;

    @Column(nullable = false)
    private Long offeringPrice;

    @Column(nullable = false)
    private Integer subscriptionQty;

    @Column(nullable = false)
    private Integer allocatedQty;

    @Column(nullable = false)
    private Integer soldQty;

    private LocalDate soldDate;

    private Long soldPrice;

    @Column(nullable = false)
    private Long taxAndFee = 0L;

    @Column(nullable = false)
    private Long subscriptionFee = 0L;

    @Column(name = "SUBSCRIPTION_YEAR", nullable = false)
    private Integer year;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }

    public String getBroker() { return broker; }
    public void setBroker(String broker) { this.broker = broker; }

    public Long getOfferingPrice() { return offeringPrice; }
    public void setOfferingPrice(Long offeringPrice) { this.offeringPrice = offeringPrice; }

    public Integer getSubscriptionQty() { return subscriptionQty; }
    public void setSubscriptionQty(Integer subscriptionQty) { this.subscriptionQty = subscriptionQty; }

    public Integer getAllocatedQty() { return allocatedQty; }
    public void setAllocatedQty(Integer allocatedQty) { this.allocatedQty = allocatedQty; }

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

    @Transient
    public Long getProfit() {
        if (soldPrice == null || allocatedQty == null || offeringPrice == null) return null;
        return (soldPrice - offeringPrice) * allocatedQty - taxAndFee - subscriptionFee;
    }

    @Transient
    public Double getProfitRate() {
        Long profit = getProfit();
        if (profit == null || allocatedQty == 0 || offeringPrice == 0) return null;
        return (double) profit / (offeringPrice * allocatedQty) * 100.0;
    }
}
