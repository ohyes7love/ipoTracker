package com.ipo.manager.domain;

/**
 * 공모주 청약 체크리스트 도메인
 *
 * 계좌(경록/지선/하준/하민)별 신청 여부, 배정 수량, 환불 여부, 청약내역 등록 여부를
 * 종목 단위로 추적합니다. accounts 필드는 JSON 문자열로 저장됩니다.
 *
 * DB 컬럼: corp_name(PK), kok_idx, subscription_start_date, subscription_end_date,
 *          listing_date, offering_price, accounts(JSON TEXT)
 */
public class IpoChecklist {

    /** kokstock 종목명 (PK) */
    private String corpName;

    /** kokstock 팝업 IDX (상세 조회용) */
    private String kokIdx;

    private String subscriptionStartDate;
    private String subscriptionEndDate;
    private String listingDate;
    private Long   offeringPrice;

    /**
     * 계좌별 상태 JSON 문자열.
     * 형식: {"경록":{"applied":true,"qty":5,"refunded":true,"registered":false}, ...}
     */
    private String accounts;

    public String getCorpName()              { return corpName; }
    public void   setCorpName(String v)      { corpName = v; }

    public String getKokIdx()                { return kokIdx; }
    public void   setKokIdx(String v)        { kokIdx = v; }

    public String getSubscriptionStartDate() { return subscriptionStartDate; }
    public void   setSubscriptionStartDate(String v) { subscriptionStartDate = v; }

    public String getSubscriptionEndDate()   { return subscriptionEndDate; }
    public void   setSubscriptionEndDate(String v)   { subscriptionEndDate = v; }

    public String getListingDate()           { return listingDate; }
    public void   setListingDate(String v)   { listingDate = v; }

    public Long   getOfferingPrice()         { return offeringPrice; }
    public void   setOfferingPrice(Long v)   { offeringPrice = v; }

    public String getAccounts()              { return accounts; }
    public void   setAccounts(String v)      { accounts = v; }
}
