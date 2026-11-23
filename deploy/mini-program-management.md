# 小程序轮播图配置

部署前备份数据库，执行 `mini-program-management.sql`，新增轮播图表、系统管理下的“小程序管理”菜单和 `miniProgram:manage` 权限；继承已有系统管理角色授权。菜单脚本可重复执行。首次初始化将原首页两张图片转为可编辑记录；以后不再从小程序代码读取硬编码图片。

部署后端与管理端后，重新登录后台刷新菜单和权限，进入“系统管理 → 小程序管理”。支持新增/编辑/删除、图片上传（JPG/PNG/GIF、5MB以内）或填写 HTTP/HTTPS 地址、排序值（小的在前，同序按ID）、启用/停用及预览。

微信开发者工具重新编译小程序。首页每次显示请求 `GET /api/mini-program/banners`，只返回启用的轮播图；全部删除或停用时隐藏轮播区域。图片仅展示，不再跳转原来的示例文章。上传复用配置项 `febs.uploadPath` 的持久化目录，以现有 `/imagesWeb/` 地址访问；保存配置前不会影响已发布的轮播图。正式版外部图片地址需要符合微信下载域名要求。

后台接口均要求 `miniProgram:manage` 权限：`GET/POST/PUT /system/mini-program/banners`、`DELETE /system/mini-program/banners/{id}`、`POST /system/mini-program/images`。小程序接口只读且无需后台登录。

此版本若连同尚未部署的优惠券结算功能发布，还须先执行 `coupon-checkout.sql`。构建前端后再运行现有部署脚本，避免仅后端更新导致菜单组件不存在。
