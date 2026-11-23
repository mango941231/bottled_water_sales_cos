-- Existing installations only: coupon-management.sql must already be applied.
-- Safe to repeat. No coupon or issued-coupon data is changed.
START TRANSACTION;

SET @commodity_menu_id = (
    SELECT MENU_ID FROM t_menu
    WHERE PATH = '/manage/commodity' OR COMPONENT LIKE '%commodity/Commodity%'
    ORDER BY MENU_ID LIMIT 1
);

INSERT INTO t_menu (PARENT_ID, MENU_NAME, PATH, COMPONENT, PERMS, ICON, TYPE, ORDER_NUM, CREATE_TIME, MODIFY_TIME)
SELECT product.PARENT_ID, '优惠券管理', '/manage/coupon', 'manage/coupon/Coupon', '', 'gift', '0',
       COALESCE(product.ORDER_NUM, 0) + 0.1, NOW(), NOW()
FROM t_menu product
WHERE product.MENU_ID = @commodity_menu_id
  AND NOT EXISTS (SELECT 1 FROM t_menu coupon WHERE coupon.PATH = '/manage/coupon');

UPDATE t_menu coupon
JOIN t_menu product ON product.MENU_ID = @commodity_menu_id
SET coupon.PARENT_ID = product.PARENT_ID,
    coupon.MENU_NAME = '优惠券管理',
    coupon.COMPONENT = 'manage/coupon/Coupon',
    coupon.ICON = 'gift',
    coupon.TYPE = '0',
    coupon.ORDER_NUM = COALESCE(product.ORDER_NUM, 0) + 0.1,
    coupon.MODIFY_TIME = NOW()
WHERE coupon.PATH = '/manage/coupon';

INSERT INTO t_role_menu (ROLE_ID, MENU_ID)
SELECT DISTINCT product_role.ROLE_ID, coupon.MENU_ID
FROM t_role_menu product_role
JOIN t_menu coupon ON coupon.PATH = '/manage/coupon'
WHERE product_role.MENU_ID = @commodity_menu_id
  AND NOT EXISTS (
      SELECT 1 FROM t_role_menu existing
      WHERE existing.ROLE_ID = product_role.ROLE_ID AND existing.MENU_ID = coupon.MENU_ID
  );

COMMIT;
