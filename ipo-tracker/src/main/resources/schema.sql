CREATE TABLE IF NOT EXISTS broker_fee (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    broker_name VARCHAR(50) NOT NULL UNIQUE,
    fee_amount  BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ipo_stock (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_name     VARCHAR(100) NOT NULL UNIQUE,
    offering_price BIGINT
);

CREATE TABLE IF NOT EXISTS ipo_subscription (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_name              VARCHAR(100) NOT NULL,
    broker                  VARCHAR(50)  NOT NULL,
    offering_price          BIGINT       NOT NULL,
    sold_qty                INT          NOT NULL DEFAULT 0,
    sold_date               DATE,
    sold_price              BIGINT,
    tax_and_fee             BIGINT       NOT NULL DEFAULT 0,
    subscription_fee        BIGINT       NOT NULL DEFAULT 0,
    subscription_year       INT          NOT NULL,
    subscription_start_date DATE,
    subscription_end_date   DATE,
    listing_date            DATE,
    accounts                TEXT         COMMENT '참여 계좌 목록 (쉼표 구분, 예: 경록,지선)'
);

-- 청약 체크리스트: 계좌별(경록/지선/하준/하민) 신청/배정/환불/등록 상태 관리
CREATE TABLE IF NOT EXISTS ipo_checklist (
    corp_name               VARCHAR(100) NOT NULL PRIMARY KEY,
    kok_idx                 VARCHAR(20),
    subscription_start_date VARCHAR(10),
    subscription_end_date   VARCHAR(10),
    listing_date            VARCHAR(10),
    offering_price          BIGINT,
    accounts                TEXT         COMMENT '계좌별 상태 JSON {"경록":{"applied":true,"qty":5,"refunded":true,"registered":false},...}'
);

