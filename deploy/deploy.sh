#!/usr/bin/env bash
#
# 桶装水销售配送系统 - Tomcat 部署脚本（服务器端执行）
#
# 用法：
#   scp backend/target/febs.war root@服务器IP:/tmp/
#   ssh root@服务器IP 'bash /tmp/deploy.sh'
#
# 可通过环境变量覆盖默认值：
#   TOMCAT_HOME=/opt/tomcat9 APP_NAME=febs bash deploy.sh

set -euo pipefail

# ---------- 配置 ----------
TOMCAT_HOME="${TOMCAT_HOME:-/opt/tomcat9}"
APP_NAME="${APP_NAME:-febs}"
WAR_FILE="${WAR_FILE:-/tmp/${APP_NAME}.war}"
UPLOAD_DIR="${UPLOAD_DIR:-/opt/tomcat9/webapps/${APP_NAME}/upload}"
BACKUP_DIR="${BACKUP_DIR:-/opt/tomcat9/backup}"

echo "==> Tomcat:    ${TOMCAT_HOME}"
echo "==> 应用名:    ${APP_NAME}"
echo "==> war 文件:  ${WAR_FILE}"

# ---------- 检查环境 ----------
if [ ! -d "${TOMCAT_HOME}" ]; then
    echo "[错误] Tomcat 目录不存在: ${TOMCAT_HOME}" >&2
    exit 1
fi

if [ ! -f "${WAR_FILE}" ]; then
    echo "[错误] war 文件不存在: ${WAR_FILE}" >&2
    exit 1
fi

# 检查 Tomcat 版本，必须为 9.x（Spring Boot 2.1 基于 Servlet 4.0）
TOMCAT_MAJOR=$("${TOMCAT_HOME}/bin/version.sh" 2>/dev/null | grep -oP 'Server version: Apache Tomcat/\K[0-9]+' || echo "")
if [ -n "${TOMCAT_MAJOR}" ] && [ "${TOMCAT_MAJOR}" != "9" ]; then
    echo "[警告] 当前 Tomcat 主版本为 ${TOMCAT_MAJOR}，本项目要求 Tomcat 9.x（Servlet 4.0）" >&2
fi

# ---------- 停止 Tomcat ----------
echo "==> 停止 Tomcat..."
"${TOMCAT_HOME}/bin/shutdown.sh" 2>/dev/null || true
sleep 5
# 兜底：强制结束残留进程
pkill -f "catalina.home=${TOMCAT_HOME}" 2>/dev/null || true
sleep 2

# ---------- 备份旧版本 ----------
if [ -f "${TOMCAT_HOME}/webapps/${APP_NAME}.war" ]; then
    echo "==> 备份旧版本..."
    mkdir -p "${BACKUP_DIR}"
    cp "${TOMCAT_HOME}/webapps/${APP_NAME}.war" "${BACKUP_DIR}/${APP_NAME}.war.$(date +%Y%m%d%H%M%S)"
fi

# ---------- 清理旧的展开目录 ----------
# 必须删除，否则残留的旧 class 文件会覆盖新版本，导致"改了代码不生效"
echo "==> 清理旧部署目录..."
rm -rf "${TOMCAT_HOME}/webapps/${APP_NAME}"
rm -f  "${TOMCAT_HOME}/webapps/${APP_NAME}.war"

# ---------- 部署新版本 ----------
echo "==> 部署新版本..."
cp "${WAR_FILE}" "${TOMCAT_HOME}/webapps/${APP_NAME}.war"

# ---------- 创建上传目录 ----------
mkdir -p "${UPLOAD_DIR}"
chmod 755 "${UPLOAD_DIR}"

# ---------- 启动 Tomcat ----------
echo "==> 启动 Tomcat..."
"${TOMCAT_HOME}/bin/startup.sh"

sleep 10

# ---------- 健康检查 ----------
HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:8080/${APP_NAME}/actuator/health" || echo "000")
if [ "${HTTP_CODE}" = "200" ]; then
    echo "==> 部署成功，健康检查通过 (HTTP ${HTTP_CODE})"
    echo "==> 访问地址: http://<服务器IP>:8080/${APP_NAME}/"
else
    echo "[警告] 健康检查返回 HTTP ${HTTP_CODE}，请查看日志: ${TOMCAT_HOME}/logs/catalina.out" >&2
fi

echo "==> 日志目录: ${TOMCAT_HOME}/logs"
