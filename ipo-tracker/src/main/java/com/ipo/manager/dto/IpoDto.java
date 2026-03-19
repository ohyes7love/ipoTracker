package com.ipo.manager.dto;

import com.ipo.manager.domain.IpoSubscription;
import java.time.LocalDate;

public class IpoDto {
    private Long id;
    private String stockName;
    private String broker;
    private Long offeringPrice;
    private Integer soldQty;
    private LocalDate soldDate;
    private Long soldPrice;
    private Long taxAndFee;
    private Long subscriptionFee;
    private Long profit;
    private Double profitRate;
    private Integer year;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionEndDate;
    private LocalDate listingDate;
    private String accounts;
    private boolean withdrawn;

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

    public IpoSubscription toEntity() {
        IpoSubscription entity = new IpoSubscription();
        entity.setId(this.id);
        entity.setStockName(this.stockName);
        entity.setBroker(this.broker);
        entity.setOfferingPrice(this.offeringPrice);
        entity.setSoldQty(this.soldQty != null ? this.soldQty : 0);
        entity.setSoldDate(this.soldDate);
        entity.setSoldPrice(this.soldPrice);
        entity.setTaxAndFee(this.taxAndFee != null ? this.taxAndFee : 0L);
        entity.setSubscriptionFee(this.subscriptionFee != null ? this.subscriptionFee : 0L);
        entity.setYear(this.year);
        entity.setSubscriptionStartDate(this.subscriptionStartDate);
        entity.setSubscriptionEndDate(this.subscriptionEndDate);
        entity.setListingDate(this.listingDate);
        entity.setAccounts(this.accounts);
        entity.setWithdrawn(this.withdrawn);
        return entity;
    }

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
    public Long getProfit() { return profit; }
    public void setProfit(Long profit) { this.profit = profit; }
    public Double getProfitRate() { return profitRate; }
    public void setProfitRate(Double profitRate) { this.profitRate = profitRate; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public LocalDate getSubscriptionStartDate() { return subscriptionStartDate; }
    public void setSubscriptionStartDate(LocalDate subscriptionStartDate) { this.subscriptionStartDate = subscriptionStartDate; }
    public LocalDate getSubscriptionEndDate() { return subscriptionEndDate; }
    public void setSubscriptionEndDate(LocalDate subscriptionEndDate) { this.subscriptionEndDate = subscriptionEndDate; }
    public LocalDate getListingDate() { return listingDate; }
    public void setListingDate(LocalDate listingDate) { this.listingDate = listingDate; }
    public String getAccounts() { return accounts; }
    public void setAccounts(String accounts) { this.accounts = accounts; }
    public boolean isWithdrawn() { return withdrawn; }
    public void setWithdrawn(boolean withdrawn) { this.withdrawn = withdrawn; }
}
