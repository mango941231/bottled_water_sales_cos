-- Apply once after water-ticket.sql has been deployed.
-- Ticket package prices preserve the previous price as both regular and sale price.
ALTER TABLE water_ticket_package
    ADD COLUMN original_price DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER price,
    ADD COLUMN discount_price DECIMAL(10,2) NOT NULL DEFAULT 0 AFTER original_price;

UPDATE water_ticket_package
SET original_price = price, discount_price = price
WHERE original_price = 0 AND discount_price = 0;

CREATE TABLE coupon_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    coupon_type VARCHAR(16) NOT NULL COMMENT 'ALL=全场通用 PRODUCT=指定商品',
    discount_type VARCHAR(16) NOT NULL COMMENT 'AMOUNT=立减 RATE=折扣',
    discount_value DECIMAL(10,2) NOT NULL,
    min_spend DECIMAL(10,2) NOT NULL DEFAULT 0,
    valid_days INT NOT NULL,
    start_time DATETIME NULL,
    end_time DATETIME NULL,
    total_count INT NULL COMMENT 'NULL表示不限量',
    claimed_count INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_coupon_template_status_time (status, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

CREATE TABLE coupon_commodity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    coupon_id BIGINT NOT NULL,
    commodity_id INT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_commodity (coupon_id, commodity_id),
    KEY idx_coupon_commodity_product (commodity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品专用优惠券适用商品';

CREATE TABLE user_coupon (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    coupon_id BIGINT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=未使用 1=已使用 2=已过期',
    receive_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_coupon_once (user_id, coupon_id),
    KEY idx_user_coupon_user_status (user_id, status, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户已领取优惠券';

-- Create a submenu under the existing product-management menu and grant the same roles.
INSERT INTO t_menu (PARENT_ID, MENU_NAME, PATH, COMPONENT, PERMS, ICON, TYPE, ORDER_NUM, CREATE_TIME, MODIFY_TIME)
SELECT parent.MENU_ID, '优惠券管理', '/manage/coupon', 'manage/commodity/Coupon', '', 'gift', '0', 3, NOW(), NOW()
FROM t_menu parent
WHERE (parent.PATH = '/manage/commodity' OR parent.COMPONENT LIKE '%commodity/Commodity%')
  AND NOT EXISTS (SELECT 1 FROM t_menu existing WHERE existing.PATH = '/manage/coupon')
ORDER BY parent.MENU_ID
LIMIT 1;

INSERT IGNORE INTO t_role_menu (ROLE_ID, MENU_ID)
SELECT parent_roles.ROLE_ID, coupon_menu.MENU_ID
FROM t_role_menu parent_roles
JOIN t_menu parent ON parent.MENU_ID = parent_roles.MENU_ID
JOIN t_menu coupon_menu ON coupon_menu.PATH = '/manage/coupon'
WHERE parent.PATH = '/manage/commodity' OR parent.COMPONENT LIKE '%commodity/Commodity%';
