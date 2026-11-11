-- These existing page records were incorrectly seeded as buttons (TYPE=1).
-- Do not change actual action/button permissions or role assignments.
START TRANSACTION;
UPDATE t_menu SET TYPE = '0'
WHERE TYPE = '1' AND PARENT_ID IN (2, 3, 4, 5)
  AND MENU_ID IN (10,11,12,13,14,15,16,17,18,19,20,30,31,32,33,34,40,41,42,43,44,45,46,50)
  AND PATH IS NOT NULL AND PATH <> ''
  AND COMPONENT IS NOT NULL AND COMPONENT <> '';
COMMIT;
