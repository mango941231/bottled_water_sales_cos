-- Align already-assigned page permissions with existing controller annotations.
-- Does not grant action permissions or change role/menu assignments.
START TRANSACTION;
UPDATE t_menu SET PERMS='user:view' WHERE MENU_ID=30 AND PERMS='system:user:list';
UPDATE t_menu SET PERMS='role:view' WHERE MENU_ID=31 AND PERMS='system:role:list';
UPDATE t_menu SET PERMS='menu:view' WHERE MENU_ID=32 AND PERMS='system:menu:list';
UPDATE t_menu SET PERMS='dept:view' WHERE MENU_ID=33 AND PERMS='system:dept:list';
UPDATE t_menu SET PERMS='dict:view' WHERE MENU_ID=34 AND PERMS='system:dict:list';
UPDATE t_menu SET PERMS='user:online' WHERE MENU_ID=40 AND PERMS='monitor:online:list';
UPDATE t_menu SET PERMS='log:view' WHERE MENU_ID=41 AND PERMS='monitor:log:list';
COMMIT;
