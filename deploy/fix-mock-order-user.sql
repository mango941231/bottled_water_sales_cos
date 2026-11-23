-- One-time repair of the known development fixtures on the current test server.
-- Back up these tables before running. Shop users 1 and 2 remain shop users.
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO user_info (code, user_name, avatar, open_id, sex, type, create_date)
SELECT 'MOCK-DEV-USER', '测试用户', '/img/logo.png', 'mock-dev-user', 1, 1, NOW()
WHERE NOT EXISTS (SELECT 1 FROM user_info WHERE code = 'MOCK-DEV-USER');
SET @mock_user_id = (SELECT id FROM user_info WHERE code = 'MOCK-DEV-USER' AND type = 1 LIMIT 1);

UPDATE address_info SET user_id = @mock_user_id
WHERE id = 1 AND user_id = 1 AND name = '测试用户' AND phone = '13800138000';
UPDATE order_info oi
JOIN commodity_info ci ON ci.id = oi.commodity_id
JOIN address_info ai ON ai.id = 1 AND ai.user_id = @mock_user_id
SET oi.user_id = @mock_user_id, oi.shop_id = ci.shop_id, oi.address_id = ai.id
WHERE oi.id = 1 AND oi.user_id = 1 AND oi.code = 'ORD-1794803453737';
UPDATE order_detail od
JOIN order_info oi ON oi.code = od.code AND oi.user_id = @mock_user_id
SET od.user_id = @mock_user_id, od.address_id = oi.address_id
WHERE od.id = 1 AND od.user_id = 1 AND od.code = 'ORD-1794803453737';
UPDATE water_ticket_account SET user_id = @mock_user_id
WHERE id = 1 AND user_id = 1 AND commodity_id = 3;
UPDATE water_ticket_usage SET user_id = @mock_user_id
WHERE id = 1 AND user_id = 1 AND account_id = 1 AND order_code = 'ORD-1794803453737';

COMMIT;
SELECT @mock_user_id AS mock_user_id;
