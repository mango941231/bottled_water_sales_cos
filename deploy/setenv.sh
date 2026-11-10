#!/usr/bin/env bash
#
# Tomcat 环境变量配置（部署到服务器后执行一次）
#   cp setenv.sh /opt/tomcat9/bin/setenv.sh
#   chmod +x /opt/tomcat9/bin/setenv.sh
#
# Tomcat 会自动加载 bin/setenv.sh

# JVM 参数
export JAVA_OPTS="-Xms512m -Xmx1024m -Xss512k -Dfile.encoding=UTF-8 -Djava.awt.headless=true"

# 激活 Spring Boot 生产环境配置（对应 application-prod.yml）
export SPRING_PROFILES_ACTIVE=prod

# 数据库配置
export DB_HOST="数据库IP"
export DB_PORT="3306"
export DB_NAME="tongzhuangshui"
export DB_USERNAME="数据库用户"
export DB_PASSWORD="数据库密码"

# Redis 配置
export REDIS_HOST="Redis_IP"
export REDIS_PORT="6379"
export REDIS_PASSWORD=""

# 文件上传目录与日志目录（必须使用绝对路径）
export UPLOAD_PATH="/opt/tomcat9/webapps/febs/upload"
export LOG_PATH="/opt/tomcat9/logs"
