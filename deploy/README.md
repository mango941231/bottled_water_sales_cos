# 当前云服务器部署

本项目使用本机修改 → Git push → 服务器 git pull → 构建并部署的流程。

## 已统一的访问路径

- 网站：http://81.68.239.216/
- 后端：http://81.68.239.216/febs/
- 健康检查：http://81.68.239.216/febs/actuator/health
- Tomcat 直连：http://127.0.0.1:8233/febs/actuator/health

Tomcat：`/usr/local/bttomcat/tomcat9`，启动脚本：`/etc/init.d/bttomcat9`。
业务应用独立部署在 `/var/lib/febs/webapps/febs.war`，不再与 Tomcat 默认 ROOT 共用展开目录。
Java 8 和 Maven 安装在 `/opt/febs-tools`，不修改系统默认 Java。

## 日常更新

本机仓库根目录：

```powershell
cd /myproject/bottled_water_sales_cos
git pull --ff-only origin main
bash deploy/deploy.sh
```

`NODE_OPTIONS` 仅用于此旧版 Webpack 与较新 Node 的兼容。未修改前端时可跳过前端构建。

服务器：

```bash
cd /myproject/bottled_water_sales_cos
git pull --ff-only origin main
bash deploy/deploy.sh
```

脚本首先构建和测试，失败不会中断现有服务；成功后备份、停止 Tomcat、配置独立应用目录、部署 WAR，再验证健康检查。切换失败会恢复旧配置和 WAR。不要再执行原来的 `/opt/tomcat9:8080` 部署命令。

第一次执行会下载并校验官方 Corretto Java 8 和 Maven；后续复用已有工具。需要 root、Python 3、curl、tar，以及可访问下载源的网络。

## 环境配置与数据

- 数据库、Redis 等环境变量：`/etc/febs/runtime.env`，root 可读，不提交 Git。
- 首次执行从宝塔现有 `bin/setenv.sh` 迁移凭据，数据库与 Redis 使用本机回环地址。
- 上传目录：`/var/lib/febs/upload`，不在 WAR 展开目录内。
- 应用日志：`/var/log/febs`。
- Tomcat 日志：`/www/wwwlogs/neizhi9/catalina-daemon.out`。
- 每次配置和 WAR 备份：`/var/backups/febs/<时间戳>`；其中可能含凭据，仅 root 可读。
- 前端静态目录：仓库的 `frontend/dist`。
- Nginx 配置：`/www/server/panel/vhost/nginx/java_febs.conf`，由部署脚本生成并校验。

已有历史上传文件如在其他目录，应迁移至 `/var/lib/febs/upload` 并将所有者设为 `www:www`。不要删除历史目录直到确认迁移完成。

`deploy/nginx.conf` 是通用示例；当前宝塔服务器的实际配置以 `configure-baota.py` 生成内容为准。不要把示例 setenv.sh 中的占位凭据覆盖到生产环境。

## 验证与排障

```bash
curl -i http://127.0.0.1:8233/febs/actuator/health
curl -i http://81.68.239.216/febs/actuator/health
curl -i http://81.68.239.216/febs/user
```

健康检查应为 HTTP 200、`{"status":"UP"}`，它会检查数据库与 Redis。未登录访问受保护接口应返回 401，而不是 Tomcat 的 HTML 404。

生产环境仅暴露 Actuator health，且不公开依赖详情；其他接口的 JWT 认证保持启用。JWT 白名单按应用内路径匹配，因此 `/febs/login` 可以匹配 `/login`。

脚本配置针对当前 IP 和目录；迁移服务器前请修改 `configure-baota.py` 中的主机名、静态目录和 Nginx 路径。
