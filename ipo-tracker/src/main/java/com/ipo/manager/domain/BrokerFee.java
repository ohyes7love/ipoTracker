package com.ipo.manager.domain;

public class BrokerFee {

    private Long id;
    private String brokerName;
    private Long feeAmount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBrokerName() { return brokerName; }
    public void setBrokerName(String brokerName) { this.brokerName = brokerName; }

    public Long getFeeAmount() { return feeAmount; }
    public void setFeeAmount(Long feeAmount) { this.feeAmount = feeAmount; }
}
