package com.ipo.manager.domain;

public class IpoStock {

    private Long id;
    private String stockName;
    private Long offeringPrice;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }

    public Long getOfferingPrice() { return offeringPrice; }
    public void setOfferingPrice(Long offeringPrice) { this.offeringPrice = offeringPrice; }
}
