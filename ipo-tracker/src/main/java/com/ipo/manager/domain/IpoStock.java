package com.ipo.manager.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "IPO_STOCK")
public class IpoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String stockName;

    @Column(nullable = false)
    private Long offeringPrice;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }

    public Long getOfferingPrice() { return offeringPrice; }
    public void setOfferingPrice(Long offeringPrice) { this.offeringPrice = offeringPrice; }
}
