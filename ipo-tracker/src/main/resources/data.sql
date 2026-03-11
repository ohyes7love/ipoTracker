INSERT INTO broker_fee (broker_name, fee_amount) VALUES
('미래에셋', 2000), ('한국투자', 2000), ('삼성', 2000),
('NH', 2000), ('대신', 2000), ('하나', 2000), ('하이', 2000),
('DB', 2000), ('기흥', 2000), ('KB', 1500), ('신한', 2000),
('유진', 2000), ('교보', 2000), ('유안타', 3000), ('신영', 2000),
('IBK', 1500), ('한화', 2000)
ON DUPLICATE KEY UPDATE fee_amount = VALUES(fee_amount);

INSERT INTO ipo_stock (stock_name, offering_price) VALUES
('세미파이브', 22500),
('IBKS제25호스팩', 2000)
ON DUPLICATE KEY UPDATE offering_price = VALUES(offering_price);

INSERT IGNORE INTO ipo_subscription
(stock_name, broker, offering_price, sold_qty, sold_date, sold_price, tax_and_fee, subscription_fee, subscription_year)
VALUES
('세미파이브', '삼성', 22500, 2, '2025-12-29', 60000, 129, 2000, 2025),
('IBKS제25호스팩', 'IBK', 2000, 8, '2025-12-19', 6000, 0, 1500, 2025);
