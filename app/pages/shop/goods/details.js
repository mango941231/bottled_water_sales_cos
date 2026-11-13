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
        this.setData({ commoditId: options.commoditId })
        this.getGoodsDetail(options.commoditId)
        this.getEvaluationByGoods(options.commoditId)
        this.getTicketPackages(options.commoditId)
    },
    getGoodsDetail(commodityId) {
        http.get('goodsDetail', { commodityId }).then((r) => {
            const goods = r.data
            this.setData({
                swiperlist: goods.images.split(','),
                goods
            })
        })
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
    shopDetail() {
        wx.navigateTo({
			url: '/pages/shop/index/index?shopId='+this.data.goods.shopId+''
		});
    },
    message() {
        wx.getStorage({
            key: 'userInfo',
            success: (res) => {
                wx.navigateTo({
                    url: '/pages/user/detail/index?takeUser=' + this.data.goods.userId + '&sendUser=' + res.data.id + '&otherName=' + this.data.goods.userName + ''
                });
            },
            fail: res => {
                wx.showToast({
                    title: '请先进行登录',
                    icon: 'none',
                    duration: 2000
                })
            }
        })
    },
    cartView() {
        wx.switchTab({
            url: '/pages/scar/index/index'
        })
    },
    addCart() {
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
