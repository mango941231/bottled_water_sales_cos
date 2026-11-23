const app = getApp();
let http = require('../../../utils/request')
Page({
    data: {
        StatusBar: app.globalData.StatusBar + 6,
        imageBaseUrl: app.globalData.imageBaseUrl,
        TabbarBot: app.globalData.tabbar_bottom,
        swiperlist: [],
        goods: null,
        commoditId: null,
        evaluation: [],
        ticketPackages: [],
        buyTicketBusy: false
    },
    onLoad: function (options) {
        const commodityId = options.commodityId || options.commoditId
        this.setData({ commoditId: commodityId })
        this.getGoodsDetail(commodityId)
        this.getEvaluationByGoods(commodityId)
        this.getTicketPackages(commodityId)
    },
    getGoodsDetail(commodityId) {
        return http.get('goodsDetail', { commodityId }).then((r) => {
            let goods = r
            for (let i = 0; i < 3 && goods && goods.data && typeof goods.data === 'object'; i++) {
                goods = goods.data
            }
            if (!goods || goods.id == null) {
                throw new Error('商品信息不存在')
            }
            const rawImages = goods.images || goods.image || goods.imageUrl || ''
            const images = (Array.isArray(rawImages) ? rawImages : String(rawImages).split(','))
                .map(path => this.resolveImageUrl(path))
                .filter(Boolean)
            this.setData({
                swiperlist: images,
                goods: Object.assign({}, goods, { images, imageUrl: images[0] || '' })
            })
        }).catch((error) => {
            wx.showToast({ title: error.message || '商品信息加载失败', icon: 'none' })
        })
    },
    resolveImageUrl(path) {
        if (!path) return ''
        const value = String(path).trim()
        if (/^(https?:)?\/\//i.test(value)) return value
        return this.data.imageBaseUrl + value.replace(/^\/?(?:febs\/)?imagesWeb\//i, '').replace(/^\/+/, '')
    },
    onDetailImageError(e) {
        const failedIndex = Number(e.currentTarget.dataset.index)
        this.setData({ swiperlist: this.data.swiperlist.filter((item, index) => index !== failedIndex) })
    },
    getEvaluationByGoods(commodityId) {
        http.get('getEvaluationByGoods', { commodityId }).then((r) => {
            this.setData({
                evaluation: r.data
            })
        })
    },
    getTicketPackages(commodityId) {
        http.get('waterTicket/packages', { commodityId }, false).then((r) => {
            this.setData({ ticketPackages: r.data || [] })
        })
    },
    buyTicket(e) {
        if (this.data.buyTicketBusy) return
        const packageInfo = this.data.ticketPackages.find(item => String(item.id) === String(e.currentTarget.dataset.id))
        if (!packageInfo) return
        wx.getStorage({
            key: 'userInfo',
            success: (res) => {
                wx.showModal({
                    title: '购买水票',
                    content: `确认以 ${packageInfo.price} 元购买「${packageInfo.name}」？当前开发版为模拟支付，确认后立即发放。`,
                    success: (result) => {
                        if (!result.confirm) return
                        this.setData({ buyTicketBusy: true })
                        http.post('waterTicket/purchase', {
                            userId: res.data.id,
                            packageId: packageInfo.id
                        }).then(() => {
                            wx.showToast({ title: '水票已发放', icon: 'success' })
                        }).finally(() => this.setData({ buyTicketBusy: false }))
                    }
                })
            },
            fail: () => wx.showToast({ title: '请先登录', icon: 'none' })
        })
    },
    homeView() { wx.switchTab({ url: '/pages/home/index/index' }) },
    cartView() {
        wx.switchTab({
            url: '/pages/scar/index/index'
        })
    },
    addCart() {
        if (!this.data.goods) {
            wx.showToast({ title: '商品信息尚未加载完成', icon: 'none' })
            return
        }
        if (this.data.goods.onPut == 0) {
            wx.showToast({
                title: '该商品已下架',
                icon: 'none',
                duration: 2000
            })
        } else {
            wx.getStorage({
                key: 'userInfo',
                success: (res) => {
                    http.get('selUserCartByGoodsId', { userId: res.data.id, commodityId: this.data.goods.id }).then((r) => {
                        if (r.data != 0) {
                            wx.showToast({
                                title: '商品已在购物车',
                                icon: 'none',
                                duration: 2000
                            })
                        } else {
                            let data = { userId: res.data.id, commodityId: this.data.goods.id, price: this.data.goods.price, }
                            http.post('addGoodsCart', data).then((r) => {
                                wx.showToast({
                                    title: '添加购物车成功',
                                    icon: 'success',
                                    duration: 2000
                                })
                            })
                        }
                    })
                },
                fail: res => {
                    wx.showToast({
                        title: '请先进行登录',
                        icon: 'none',
                        duration: 2000
                    })
                }
            })
        }
    },
    buyGoods() {
        if (!this.data.goods) {
            wx.showToast({ title: '商品信息尚未加载完成', icon: 'none' })
            return
        }
        if (this.data.goods.onPut == 0) {
            wx.showToast({
                title: '该商品已下架',
                icon: 'none',
                duration: 2000
            })
        } else {
            wx.getStorage({
                key: 'userInfo',
                success: (res) => {
                    http.get('selDefaultAddress', { userId: res.data.id }).then((r) => {
                        if (r.data == null) {
                            wx.showToast({
                                title: '请先设置默认收货地址',
                                icon: 'none',
                                duration: 1000
                            })
                        } else {
                            wx.navigateTo({
                                url: '/pages/scar/order/index?type=2&commodityId=' + this.data.commoditId + ''
                            })
                        }
                    })
                },
                fail: res => {
                    wx.showToast({
                        title: '请先进行登录',
                        icon: 'none',
                        duration: 2000
                    })
                }
            })

        }
    }
});
