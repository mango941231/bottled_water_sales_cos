-- Run before deploying the single-store release. Existing balances and orders are preserved.
ALTER TABLE water_ticket_package MODIFY COLUMN shop_id INT NULL DEFAULT NULL;
ALTER TABLE water_ticket_account MODIFY COLUMN shop_id INT NULL DEFAULT NULL;
ALTER TABLE water_ticket_usage MODIFY COLUMN shop_id INT NULL DEFAULT NULL;

START TRANSACTION;
CREATE TEMPORARY TABLE obsolete_store_menu_ids (id BIGINT PRIMARY KEY);
INSERT INTO obsolete_store_menu_ids (id)
SELECT MENU_ID FROM t_menu WHERE
    PATH IN ('/manage/shop', '/manage/audit', '/shop')
    OR PATH LIKE '/shop/%' OR COMPONENT LIKE 'shop/%'
    OR COMPONENT LIKE 'manage/shop/%' OR COMPONENT LIKE 'manage/audit/%';
DELETE rm FROM t_role_menu rm JOIN obsolete_store_menu_ids old ON old.id = rm.MENU_ID;
DELETE m FROM t_menu m JOIN obsolete_store_menu_ids old ON old.id = m.MENU_ID;
DROP TEMPORARY TABLE obsolete_store_menu_ids;
COMMIT;
