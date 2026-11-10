# Tomcat 部署指南

本项目后端为 **Spring Boot 2.1.0.RELEASE + JDK 8**，已改造为 WAR 模式，可部署到外部 **Tomcat 9.x**。

> 版本对应关系说明：Spring Boot 2.1 基于 Servlet 4.0 规范，内嵌 Tomcat 版本为 9.0.12。
> 因此外部容器**必须**使用 Tomcat 9.x。Tomcat 8.5 不支持 Servlet 4.0，Tomcat 10 因 `javax.*` 改名为 `jakarta.*` 会直接启动失败。

---

## 一、服务器环境准备

| 依赖 | 版本要求 | 说明 |
|---|---|---|
| JDK | **1.8** | Boot 2.1 在 JDK 11+ 会有反射警告，JDK 17 直接启动失败 |
| Tomcat | **9.x**（建议 9.0.8x） | 必须 9.x，原因见上 |
| MySQL | 5.7 / 8.0 | 驱动为 `com.mysql.cj.jdbc.Driver`（MySQL 8 驱动） |
| Redis | 5.0+ | 用于 token 缓存 |
| Nginx | 可选 | 前后端同域 + 反向代理 |

安装 Tomcat：

```bash
# 下载 Tomcat 9
cd /opt
wget https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.85/bin/apache-tomcat-9.0.85.tar.gz
tar -zxvf apache-tomcat-9.0.85.tar.gz
mv apache-tomcat-9.0.85 tomcat9

# 确认 JDK 版本
java -version   # 应为 1.8

# 关闭 Tomcat 默认示例应用
rm -rf /opt/tomcat9/webapps/docs /opt/tomcat9/webapps/examples \
       /opt/tomcat9/webapps/host-manager /opt/tomcat9/webapps/manager
```

---

## 二、后端打包

```bash
cd backend
mvn clean package -DskipTests
```

产物：`backend/target/febs.war`

> `febs.war.original` 是原始 war，**部署时用 `febs.war`**（经过 spring-boot repackage，Tomcat 部署两者都可以，但用 `febs.war` 兼容性更好）。

### 已做的 WAR 化改造

| 文件 | 改动 |
|---|---|
| `pom.xml` | `packaging` 改为 `war`；`spring-boot-starter-tomcat` 设为 `provided`；`maven-war-plugin` 配置 `failOnMissingWebXml=false`；`finalName=febs` |
| `FebsApplication.java` | 继承 `SpringBootServletInitializer`，实现 `configure()`，保留 `main` 方法 |
| `application-prod.yml` | 新增生产配置模板，敏感信息走环境变量 |
| `FileController.java` | 上传目录硬编码路径改为读取 `febs.uploadPath` |
| `MyWebMvcConfigurerAdapter.java` | 图片访问路径改为读取 `febs.uploadPath` |
| `logback-spring.xml` | 日志目录支持 `LOG_PATH` 环境变量覆盖 |

---

## 三、配置生产环境变量

```bash
cp deploy/setenv.sh /opt/tomcat9/bin/setenv.sh
chmod +x /opt/tomcat9/bin/setenv.sh

# 编辑填入真实的数据库、Redis 信息
vi /opt/tomcat9/bin/setenv.sh
```

Tomcat 启动时会自动加载 `bin/setenv.sh`。其中 `SPRING_PROFILES_ACTIVE=prod` 用于激活 `application-prod.yml`。

> **注意**：`setenv.sh` 权限建议设为 `600`，避免数据库密码被其他用户读取：
> ```bash
> chmod 600 /opt/tomcat9/bin/setenv.sh
> ```

---

## 四、部署后端

### 方式一：使用部署脚本（推荐）

```bash
# 1. 本地打包后上传
scp backend/target/febs.war root@服务器IP:/tmp/febs.war
scp deploy/deploy.sh root@服务器IP:/tmp/deploy.sh

# 2. 服务器上执行
ssh root@服务器IP 'bash /tmp/deploy.sh'
```

脚本会自动完成：停 Tomcat → 备份旧 war → **删除旧展开目录** → 部署新 war → 建上传目录 → 启 Tomcat → 健康检查。

> **为什么要删旧目录**：Tomcat 会把 war 展开成同名目录。如果你只是替换 war 文件而没删旧目录，残留的旧 class 文件会覆盖新版本，表现为"改了代码不生效"。这是 Tomcat 部署最常见的坑。

### 方式二：手动部署

```bash
/opt/tomcat9/bin/shutdown.sh
sleep 5

rm -rf /opt/tomcat9/webapps/febs /opt/tomcat9/webapps/febs.war

cp /tmp/febs.war /opt/tomcat9/webapps/febs.war
mkdir -p /opt/tomcat9/webapps/febs/upload

/opt/tomcat9/bin/startup.sh
```

### 验证

```bash
curl http://127.0.0.1:8080/febs/actuator/health
tail -f /opt/tomcat9/logs/catalina.out
```

访问地址：`http://服务器IP:8080/febs/`

---

## 五、前端打包与部署

### 修改接口地址

接口地址已改为从环境变量读取：

- `frontend/config/dev.env.js` → `http://127.0.0.1:9527/`（本地开发）
- `frontend/config/prod.env.js` → `/febs/`（生产，同域相对路径）

若后端在其他域名，修改 `frontend/config/prod.env.js` 的 `VUE_APP_BASE_API` 为完整地址，然后重新 `npm run build`。

### 打包

```bash
cd frontend
npm ci          # 必须用 npm ci，严格按 package-lock.json 安装
npm run build
```

产物：`frontend/dist/`

### 部署静态文件

```bash
scp -r frontend/dist/* root@服务器IP:/var/www/febs-frontend/
```

---

## 六、Nginx 配置（推荐）

```bash
cp deploy/nginx.conf /etc/nginx/conf.d/febs.conf
vi /etc/nginx/conf.d/febs.conf   # 修改 server_name 和 root 路径
nginx -t && nginx -s reload
```

作用：
- `/` → 前端静态文件
- `/febs/` → 反向代理到 `127.0.0.1:8080/febs/`
- `/imagesWeb/` → 图片访问代理

> `client_max_body_size 100m` 必须与后端 `spring.servlet.multipart.max-file-size: 100MB` 匹配，否则大文件上传会返回 413。

---

## 七、微信小程序端

`app/` 目录是小程序源码。上传前需把接口地址从本地地址改为线上域名：

```bash
cd app
grep -rn "127.0.0.1\|localhost" .
```

微信小程序要求：
1. 域名必须**已备案**并配置 **HTTPS**
2. 在微信公众平台 → 开发管理 → 服务器域名 → **request 合法域名**中添加该域名
3. `app/project.config.json` 中 `urlCheck: false` 仅在开发者工具中生效，真机预览和正式版仍会校验域名

---

## 八、部署检查清单

- [ ] 服务器 JDK 为 1.8
- [ ] Tomcat 为 9.x（`bin/version.sh` 确认）
- [ ] `bin/setenv.sh` 已配置数据库/Redis 密码，权限 600
- [ ] `SPRING_PROFILES_ACTIVE=prod` 已设置
- [ ] 部署前已删除 `webapps/febs` 旧展开目录
- [ ] 上传目录 `UPLOAD_PATH` 为绝对路径且 Tomcat 进程有写权限
- [ ] `febs.uploadPath` 与 `MyWebMvcConfigurerAdapter` 指向同一目录
- [ ] MySQL 已创建 `tongzhuangshui` 库并导入表结构
- [ ] Redis 已启动，`spring.redis.password` 与实际一致
- [ ] Nginx `client_max_body_size` 与后端上传限制一致
- [ ] `actuator/health` 返回 200
- [ ] 浏览器能登录，图片能正常显示

---

## 九、常见问题

**启动报 `Unsupported class file major version` / `class file has wrong version`**
JDK 版本不对。确认 `java -version` 为 1.8，且 `JAVA_HOME` 指向 JDK 8。

**启动报 `javax.servlet.http.HttpServlet` 找不到**
Tomcat 版本不对，必须是 9.x。Tomcat 10 用了 `jakarta.servlet`，包名完全不同。

**改了代码但接口行为没变**
旧的展开目录没删。`rm -rf webapps/febs` 后重新部署。

**图片上传失败 / 图片 404**
`febs.uploadPath` 不是绝对路径，或 Tomcat 进程对该目录无写权限。检查 `setenv.sh` 里的 `UPLOAD_PATH`，并 `chmod 755`。

**接口 404**
上下文路径不对。war 名是 `febs.war` → 上下文 `/febs`。若想用根路径，把 war 改名为 `ROOT.war`，同时把前端 `VUE_APP_BASE_API` 改为 `'/'`。

**Redis 连接超时**
检查 `setenv.sh` 中 `REDIS_HOST`/`REDIS_PASSWORD`。若 Redis 有密码，`application-prod.yml` 里 `password: ${REDIS_PASSWORD:}` 会被空值覆盖，需显式设置。

**中文乱码**
`JAVA_OPTS` 中保留 `-Dfile.encoding=UTF-8`，同时数据库连接串需带 `characterEncoding=UTF-8`（已配置）。

---

## 十、目录结构说明

```
backend/
├── pom.xml                          # war 打包配置
└── src/main/
    ├── java/cc/mrbird/febs/
    │   ├── FebsApplication.java     # 继承 SpringBootServletInitializer
    │   ├── common/config/
    │   │   └── MyWebMvcConfigurerAdapter.java   # 图片访问路径配置化
    │   └── cos/controller/
    │       └── FileController.java  # 上传目录配置化
    └── resources/
        ├── application.yml          # 默认配置
        └── application-prod.yml     # 生产配置模板

deploy/
├── deploy.sh        # 服务器端部署脚本
├── setenv.sh        # Tomcat 环境变量配置
└── nginx.conf       # Nginx 反向代理配置
```
