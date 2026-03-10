package com.ipo.manager.dto;

public class MonthlySummaryDto {
    private int month;
    private long totalProfit;

    public MonthlySummaryDto(int month, long totalProfit) {
        this.month = month;
        this.totalProfit = totalProfit;
    }

    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }
    public long getTotalProfit() { return totalProfit; }
    public void setTotalProfit(long totalProfit) { this.totalProfit = totalProfit; }
}
