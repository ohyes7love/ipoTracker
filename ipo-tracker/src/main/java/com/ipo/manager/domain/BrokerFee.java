package com.ipo.manager.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "BROKER_FEE")
public class BrokerFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String brokerName;

    @Column(nullable = false)
    private Long feeAmount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBrokerName() { return brokerName; }
    public void setBrokerName(String brokerName) { this.brokerName = brokerName; }

    public Long getFeeAmount() { return feeAmount; }
    public void setFeeAmount(Long feeAmount) { this.feeAmount = feeAmount; }
}
