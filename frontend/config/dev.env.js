'use strict'
const merge = require('webpack-merge')
const prodEnv = require('./prod.env')

module.exports = merge(prodEnv, {
  NODE_ENV: '"development"',
  // 本地开发直连后端，端口与后端 application.yml 中的 server.port 一致
  VUE_APP_BASE_API: '"http://127.0.0.1:9527/"'
})
