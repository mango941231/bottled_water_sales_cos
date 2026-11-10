'use strict'
module.exports = {
  NODE_ENV: '"production"',
  // 生产环境后端接口地址
  // 使用同域相对路径时由 Nginx 反向代理到 Tomcat 的 /febs 上下文
  // 如果后端部署在其他域名/端口，改为完整地址，例如 '"http://api.example.com/febs/"'
  VUE_APP_BASE_API: '"/febs/"'
}
