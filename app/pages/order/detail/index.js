const app = getApp()
const http = require('../../../utils/request')
const status = require('../../../utils/order-status')
Page({
  data: {StatusBar: app.globalData.StatusBar, CustomBar: app.globalData.CustomBar, imageBaseUrl: app.globalData.imageBaseUrl, order: null},
  onLoad(options) {
    const user = wx.getStorageSync('userInfo')
    if (!user) return wx.showToast({title: '请先登录', icon: 'none'})
    this.userId = user.id
    this.orderId = options.orderId
    this.load()
  },
  load() {
    return http.get('selOrderListByOrderIds', {ids: this.orderId}).then(r => {
      const order = (r.data || [])[0]
      if (order) this.setData({order: {...order, image: order.images ? order.images.split(',')[0] : '', statusLabel: status.label(order.orderStatus)}})
    })
  },
  receipt() {
    wx.showModal({title: '确认收货', content: '确认已收到本次订购的桶装水？', success: result => {
      if (result.confirm) http.get('receipt', {orderId: this.orderId, userId: this.userId}).then(() => {
        wx.showToast({title: '收货成功'}); this.load()
      })
    }})
  }
})
