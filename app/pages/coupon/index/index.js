const app = getApp()
const http = require('../../../utils/request')

Page({
  data: {
    StatusBar: app.globalData.StatusBar,
    CustomBar: app.globalData.CustomBar,
    banner: 'https://www.crbeverage.com/indexBanner/2023-07-20/583fd836-c200-4311-9ef7-9d2e8dece839.jpg',
    tabs: [{ id: 'all', label: '全部' }, { id: 'available', label: '待领取' }, { id: 'claimed', label: '已领取' }],
    current: 'all', coupons: [], userInfo: null
  },
  onShow() { this.setData({ userInfo: wx.getStorageSync('userInfo') || null }); this.loadCoupons() },
  back() { wx.navigateBack() },
  selectTab(e) { this.setData({ current: e.currentTarget.dataset.id }); this.loadCoupons() },
  loadCoupons() {
    const userId = this.data.userInfo && this.data.userInfo.id
    return http.get('coupon/list', { userId, status: this.data.current }, false).then(r => {
      this.setData({ coupons: (r.data || []).map(item => ({
        ...item,
        valueText: item.discountType === 'RATE' ? `${Number(item.discountValue) * 10}折` : `¥${Number(item.discountValue).toFixed(0)}`,
        conditionText: Number(item.minSpend) > 0 ? `满${Number(item.minSpend)}元可用` : '无门槛可用',
        scopeText: item.couponType === 'ALL' ? '全场通用优惠券' : `指定商品：${(item.commodityNames || []).join('、')}`,
        availableText: item.endTime ? `领取后${item.validDays}天内有效` : `领取后${item.validDays}天内有效`
      })) })
    }).catch(() => this.setData({ coupons: [] }))
  },
  claim(e) {
    const userInfo = this.data.userInfo
    if (!userInfo) { wx.showToast({ title: '请先登录', icon: 'none' }); return }
    http.post('coupon/claim', { userId: userInfo.id, couponId: e.currentTarget.dataset.id }, false).then(() => {
      wx.showToast({ title: '领取成功', icon: 'success' })
      this.loadCoupons()
    }).catch(() => {})
  }
})
