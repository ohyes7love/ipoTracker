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
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_name        VARCHAR(100) NOT NULL,
    broker            VARCHAR(50)  NOT NULL,
    offering_price    BIGINT       NOT NULL,
    sold_qty          INT          NOT NULL DEFAULT 0,
    sold_date         DATE,
    sold_price        BIGINT,
    tax_and_fee       BIGINT       NOT NULL DEFAULT 0,
    subscription_fee  BIGINT       NOT NULL DEFAULT 0,
    subscription_year INT          NOT NULL
);
