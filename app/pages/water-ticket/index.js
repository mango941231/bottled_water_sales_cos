const app = getApp()
const http = require('../../utils/request')

Page({
  data: { StatusBar: app.globalData.StatusBar, CustomBar: app.globalData.CustomBar, imageBaseUrl: app.globalData.imageBaseUrl, packages: [], userInfo: null },
  onShow() {
    const userInfo = wx.getStorageSync('userInfo')
    this.setData({ userInfo: userInfo || null })
    http.get('waterTicket/packages/all', {}, false).then(r => {
      this.setData({ packages: (r.data || []).map(item => ({
        ...item,
        image: item.commodityImage ? item.commodityImage.split(',')[0] : '',
        originalPrice: Number(item.originalPrice || item.discountPrice).toFixed(2),
        discountPrice: Number(item.discountPrice || item.originalPrice).toFixed(2),
        hasDiscount: Number(item.discountPrice || item.originalPrice) < Number(item.originalPrice || item.discountPrice)
      })) })
    }).catch(() => {})
  },
  back() { wx.navigateBack() },
  purchase(e) {
    const item = this.data.packages.find(row => row.id === Number(e.currentTarget.dataset.id))
    if (!item) return
    wx.navigateTo({ url: `/pages/shop/goods/details?commoditId=${item.commodityId}` })
  }
})
