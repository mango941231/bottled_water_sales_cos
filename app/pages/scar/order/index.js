const app = getApp();
let http = require('../../../utils/request')
Page({
    data: {
        StatusBar: app.globalData.StatusBar,
        imageBaseUrl: app.globalData.imageBaseUrl,
        CustomBar: app.globalData.CustomBar,
        TabbarBot: app.globalData.tabbar_bottom,
        orderType: 0,
        orderIds: [],
        addressInfo: null,
        commodityId: null,
        orderList: [],
        allPrice: 0,
        cashPrice: 0,
        totalTicketNum: 0,
        userInfo: null
    },
    onLoad: function (options) {
        
        wx.getStorage({
            key: 'userInfo',
            success: (res) => {
                this.setData({
                    orderType: options.type,
                    userInfo: res.data,
                    commodityNum: Number(options.commodityNum || 1)
                })
                if (options.type == 1) {
                    this.setData({
                        orderIds: options.orderIds.split(',')
                    })
                    this.getOrderList(this.data.orderIds)
                } else {
                    this.setData({
                        commodityId: options.commodityId
                    })
                    this.getGoodsDetail(this.data.commodityId)
                }
                this.getUserAddress(res.data.id)
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
    getUserAddress(userId) {
        http.get('selDefaultAddress', { userId }).then((r) => {
            this.setData({ addressInfo: r.data })
        })
    },
    getGoodsDetail(commodityId) {
        http.get('goodsDetail', { commodityId }).then((r) => {
            let order = r.data
            let allPrice = Number(order.price) * this.data.commodityNum
            order.image = order.images.split(',')[0]
            order.unitPrice = Number(order.price)
            order.commodityNum = this.data.commodityNum
            order.ticketNum = 0
            this.setData({ orderList: [order], allPrice: allPrice.toFixed(2) })
            this.loadTicketBalances([order])
        })
    },
    getOrderList(orderIds) {
        http.get('selOrderListByOrderIds', { ids: orderIds.join(',') }).then((r) => {
            let allPrice = 0
            r.data.forEach(item => {
                item.image = item.images.split(',')[0]
                item.unitPrice = Number(item.price)
                item.commodityNum = Number(item.commodityNum || 1)
                item.ticketNum = 0
                allPrice += item.unitPrice * item.commodityNum
            });
            this.setData({ orderList: r.data, allPrice: allPrice.toFixed(2) })
            this.loadTicketBalances(r.data)
        })
    },
    loadTicketBalances(items) {
        Promise.all(items.map(item => http.get('waterTicket/available', {
            userId: this.data.userInfo.id,
            commodityId: item.id
        }, false).catch(() => ({ data: 0 })))).then(results => {
            const orderList = this.data.orderList.map((item, index) => ({
                ...item,
                ticketAvailable: Number(results[index].data || 0),
                ticketNum: 0
            }))
            this.setData({ orderList }, () => this.recalculate())
        })
    },
    adjustQuantity(e) {
        const index = Number(e.currentTarget.dataset.index)
        const delta = Number(e.currentTarget.dataset.delta)
        const orderList = this.data.orderList
        const item = orderList[index]
        item.commodityNum = Math.max(1, item.commodityNum + delta)
        item.ticketNum = Math.min(item.ticketNum, item.commodityNum, item.ticketAvailable || 0)
        this.setData({ orderList })
        this.recalculate()
    },
    adjustTicket(e) {
        const index = Number(e.currentTarget.dataset.index)
        const delta = Number(e.currentTarget.dataset.delta)
        const orderList = this.data.orderList
        const item = orderList[index]
        item.ticketNum = Math.max(0, Math.min(item.commodityNum, item.ticketAvailable || 0, item.ticketNum + delta))
        this.setData({ orderList })
        this.recalculate()
    },
    recalculate() {
        const allPrice = this.data.orderList.reduce((sum, item) => sum + item.unitPrice * item.commodityNum, 0)
        const cashPrice = this.data.orderList.reduce((sum, item) => sum + item.unitPrice * (item.commodityNum - item.ticketNum), 0)
        const totalTicketNum = this.data.orderList.reduce((sum, item) => sum + item.ticketNum, 0)
        this.setData({ allPrice: allPrice.toFixed(2), cashPrice: cashPrice.toFixed(2), totalTicketNum })
    },
    submit() {
        const cashPrice = this.data.orderList.reduce((sum, item) => sum + item.unitPrice * (item.commodityNum - item.ticketNum), 0)
        wx.showLoading({
            title: cashPrice > 0 ? '正在模拟支付' : '正在核销水票',
        })
        setTimeout(() => {
            if (this.data.orderType == 1) {
                const lines = this.data.orderList.map(item => ({
                    orderId: item.orderId,
                    commodityNum: item.commodityNum,
                    ticketNum: item.ticketNum
                }))
                http.post('completeCart', { userId: this.data.userInfo.id, lines }).then((r) => {
                    wx.showToast({
                        title: '支付成功',
                        icon: 'success',
                        duration: 1000
                    })
                    setTimeout(() => {
                        wx.navigateBack({ changed: true });
                    }, 1000)
                })
            } else {
                let item = this.data.orderList[0]
                let data = {
                    commodityId: this.data.commodityId,
                    commodityNum: item.commodityNum,
                    ticketNum: item.ticketNum,
                    addressId: this.data.addressInfo.id,
                    userId: this.data.userInfo.id
                }
                http.post('buyGoods', data).then((r) => {
                    wx.showToast({
                        title: '支付成功',
                        icon: 'success',
                        duration: 1000
                    })
                    setTimeout(() => {
                        wx.navigateBack({ changed: true });
                    }, 1000)
                })
            }
            wx.hideLoading()
        }, 1000)
    }
});
