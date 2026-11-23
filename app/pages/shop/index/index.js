const app = getApp()
const http = require('../../../utils/request')
Page({
  data: {
    StatusBar: app.globalData.StatusBar,
    CustomBar: app.globalData.CustomBar,
    imageBaseUrl: app.globalData.imageBaseUrl,
    goods: [], key: '', sort: 2,
    sortMenu: [{id: 2, name: '最新'}, {id: 1, name: '销量'}, {id: 3, name: '价格'}]
  },
  onLoad() { this.loadGoods() },
  loadGoods() {
    return http.get('catalog', {key: this.data.key, sort: this.data.sort}).then(r => {
      this.setData({goods: (r.data || []).map(item => ({...item, image: item.images ? item.images.split(',')[0] : ''}))})
    })
  },
  getKeyValue(e) { this.setData({key: e.detail.value}) },
  search() { this.loadGoods() },
  sortSelect(e) { this.setData({sort: Number(e.currentTarget.dataset.id)}); this.loadGoods() },
  btnback() { wx.navigateBack() }
})
