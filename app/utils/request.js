/**
 * 请求相关的封装
 */
const config = require('../config');
let baseUrl = config.API_BASE_URL; // 接口地址
let header = {
  'content-type': 'application/x-www-form-urlencoded',
  'content-type': 'application/json;charset=utf-8',
  'Authorization': "Bearer " + wx.getStorageSync("token")
}
/**
 * 封装请求
 */
function fetch(options) {
  if (options.loading) {
    wx.showLoading({
      title: '加载中',
      mask: true
    })
  }
  return new Promise((resolve, reject) => {
    wx.request({
      url: baseUrl + options.url,
      data: options.data,
      header: header,
      method: options.method,
      success: function(res) {
        if (options.loading) {
          wx.hideLoading()
        }
        let payload = res.data || {}
        // Older goodsDetail responses merge product fields into the response envelope.
        // Accept that specific format while keeping business errors rejected.
        if (options.url === 'goodsDetail' && res.statusCode === 200 &&
            payload.msg === 'success' && payload.id != null &&
            typeof payload.code === 'string' && /^COMM-/.test(payload.code)) {
          payload = { code: 0, msg: 'success', data: payload }
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(new Error(payload.message || payload.msg || '请求失败'))
          return
        }
        if (payload.code == 1) {
          // 重新登陆
          const error = new Error(payload.message || payload.msg || '登录状态已失效')
          reject(error)
          return
        }
        if (payload.code != 0) {
          const message = payload.message || payload.msg || res.errMsg || '请求失败'
          wx.showToast({
            title: message,
            mask: "true",
            icon: 'none',
            duration: 10000
          })
          reject(new Error(message))
          return;
        }
        resolve(payload); //把请求到的数据发到引用请求的地方
      },
      fail: function(err) {
        if (options.loading) {
          wx.hideLoading()
        }
        wx.showToast({
          title: "网络连接超时",
          icon: 'none',
          duration: 3000,
        })
        reject(err)
      }
    })
  })
}
/**
 * POST 请求
 */
export function post(url, params, loading = true) {
  var option = {
    url: url,
    data: params,
    method: 'POST',
    loading
  }
  return fetch(option);
}

/**
 * GET请求
 */
export function get(urls, params, loading = true) {
  var option = {
    url: urls,
    data: params,
    method: 'GET',
    loading
  }
  return fetch(option);
}
