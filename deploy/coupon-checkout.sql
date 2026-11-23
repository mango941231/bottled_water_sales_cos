-- Run once after coupon-management.sql, BEFORE deploying this backend.
-- Preserves all existing orders and coupon records.
ALTER TABLE order_info
    ADD COLUMN user_coupon_id BIGINT NULL COMMENT '核销的用户优惠券ID',
    ADD COLUMN coupon_name VARCHAR(100) NULL COMMENT '下单时优惠券名称快照',
    ADD COLUMN coupon_discount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '本订单分摊的优惠券抵扣金额',
    ADD KEY idx_order_user_coupon (user_coupon_id);
