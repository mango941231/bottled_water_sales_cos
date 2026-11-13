const app = getApp()
const http = require('../../utils/request')

Page({
  data: {
    StatusBar: app.globalData.StatusBar,
    CustomBar: app.globalData.CustomBar,
    imageBaseUrl: app.globalData.imageBaseUrl,
    accounts: [],
    userInfo: null
  },
  onShow() {
    wx.getStorage({
      key: 'userInfo',
      success: (res) => {
        this.setData({ userInfo: res.data })
        http.get('waterTicket/mine', { userId: res.data.id }).then((r) => {
          this.setData({ accounts: r.data || [] })
        })
      },
      fail: () => wx.showToast({ title: '请先登录', icon: 'none' })
    })
  }
})
