const app = getApp()
const http = require('../../../utils/request')

Page({
  data: { StatusBar: app.globalData.StatusBar, CustomBar: app.globalData.CustomBar, accounts: [] },
  onShow() {
    const userInfo = wx.getStorageSync('userInfo')
    if (!userInfo) { wx.showToast({ title: '请先登录', icon: 'none' }); this.setData({ accounts: [] }); return }
    http.get('waterTicket/mine', { userId: userInfo.id }, false).then(r => {
      this.setData({ accounts: (r.data || []).map(item => ({
        ...item,
        expireText: item.expired ? '已过期' : `有效期至 ${item.expireTime}`
      })) })
    }).catch(() => {})
  },
  back() { wx.navigateBack() }
})
