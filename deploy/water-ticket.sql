-- Water ticket packages, purchased balances and redemption ledger.
-- Run once against the application database before deploying the backend.
ALTER TABLE order_info
    ADD COLUMN commodity_num INT NOT NULL DEFAULT 1 COMMENT '商品桶数',
    ADD COLUMN ticket_num INT NOT NULL DEFAULT 0 COMMENT '水票抵扣桶数';

CREATE TABLE water_ticket_package (
    id INT NOT NULL AUTO_INCREMENT,
    shop_id INT NOT NULL,
    commodity_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    ticket_num INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    valid_days INT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ticket_package_commodity (commodity_id, status),
    KEY idx_ticket_package_shop (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='水票套餐';

CREATE TABLE water_ticket_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    shop_id INT NOT NULL,
    commodity_id INT NOT NULL,
    package_id INT NOT NULL,
    package_name VARCHAR(100) NOT NULL,
    purchase_price DECIMAL(10,2) NOT NULL,
    total_num INT NOT NULL,
    remaining_num INT NOT NULL,
    expire_time DATETIME NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ticket_account_user_goods (user_id, commodity_id, status, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户水票账户';

CREATE TABLE water_ticket_usage (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    order_code VARCHAR(64) NOT NULL,
    user_id INT NOT NULL,
    shop_id INT NOT NULL,
    commodity_id INT NOT NULL,
    ticket_num INT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ticket_usage_account (account_id),
    KEY idx_ticket_usage_order (order_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='水票使用流水';
