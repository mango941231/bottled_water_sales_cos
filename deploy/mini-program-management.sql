-- Safe to repeat. Creates configuration and the system-management submenu.
SET @banner_first_install = (
    SELECT COUNT(*) = 0 FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mini_program_banner'
);

CREATE TABLE IF NOT EXISTS mini_program_banner (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL DEFAULT '',
    image_url VARCHAR(1000) NOT NULL,
    sort_num INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_banner_status_sort (status, sort_num, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序首页轮播图';

START TRANSACTION;

SET @system_parent = (
    SELECT MENU_ID FROM t_menu WHERE MENU_NAME = '系统管理' AND PARENT_ID = 0
    ORDER BY MENU_ID LIMIT 1
);

INSERT INTO t_menu (PARENT_ID, MENU_NAME, PATH, COMPONENT, PERMS, ICON, TYPE, ORDER_NUM, CREATE_TIME, MODIFY_TIME)
SELECT @system_parent, '小程序管理', '/system/mini-program', 'system/miniProgram/MiniProgram',
       'miniProgram:manage', 'mobile', '0', 9, NOW(), NOW()
WHERE @system_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM t_menu WHERE PATH = '/system/mini-program');

UPDATE t_menu SET PARENT_ID = @system_parent, MENU_NAME = '小程序管理', COMPONENT = 'system/miniProgram/MiniProgram',
    PERMS = 'miniProgram:manage', ICON = 'mobile', TYPE = '0', MODIFY_TIME = NOW()
WHERE PATH = '/system/mini-program' AND @system_parent IS NOT NULL;

INSERT INTO t_role_menu (ROLE_ID, MENU_ID)
SELECT DISTINCT assigned.ROLE_ID, mini.MENU_ID
FROM t_role_menu assigned
JOIN t_menu mini ON mini.PATH = '/system/mini-program'
WHERE assigned.MENU_ID = @system_parent
  AND NOT EXISTS (SELECT 1 FROM t_role_menu existing WHERE existing.ROLE_ID = assigned.ROLE_ID AND existing.MENU_ID = mini.MENU_ID);

-- Keep the previous homepage images as editable initial data, only on first setup.
INSERT INTO mini_program_banner (title, image_url, sort_num, status)
SELECT '首页轮播图1', 'https://www.crbeverage.com/indexBanner/2023-07-20/583fd836-c200-4311-9ef7-9d2e8dece839.jpg', 10, 1
WHERE @banner_first_install = 1;
INSERT INTO mini_program_banner (title, image_url, sort_num, status)
SELECT '首页轮播图2', 'https://www.crbeverage.com/ybPorduct/f1225d1d-77a5-4891-9cec-6711429349ad.png', 20, 1
WHERE @banner_first_install = 1;

COMMIT;
