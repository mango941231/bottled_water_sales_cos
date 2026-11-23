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
        userInfo: null,
        couponOptions: [],
        availableCouponCount: 0,
        selectedUserCouponId: null,
        selectedCouponName: '',
        couponDiscount: '0.00',
        ticketDiscount: '0.00',
        couponModal: false,
        quoteLoading: true,
        quoteError: false,
        submitting: false
    },
    onShow() {
        if (this.data.userInfo && this.data.orderList.length) this.refreshQuote()
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
            let order = r.data || r
            if (!order || order.id == null) {
                throw new Error('商品信息不存在')
            }
            let allPrice = Number(order.price) * this.data.commodityNum
            order.image = order.images ? order.images.split(',')[0] : ''
            order.unitPrice = Number(order.price)
            order.commodityNum = this.data.commodityNum
            order.ticketNum = 0
            this.setData({ orderList: [order], allPrice: allPrice.toFixed(2) })
            this.loadTicketBalances([order])
        }).catch((error) => {
            this.setData({ quoteLoading: false, quoteError: true })
            wx.showToast({ title: error.message || '商品信息加载失败', icon: 'none' })
        })
    },
    getOrderList(orderIds) {
        http.get('selOrderListByOrderIds', { ids: orderIds.join(',') }).then((r) => {
            let allPrice = 0
            r.data.forEach(item => {
                item.image = item.images ? item.images.split(',')[0] : ''
                item.unitPrice = Number(item.price)
                item.commodityNum = Number(item.commodityNum || 1)
                item.ticketNum = 0
                allPrice += item.unitPrice * item.commodityNum
            });
            this.setData({ orderList: r.data, allPrice: allPrice.toFixed(2) })
            this.loadTicketBalances(r.data)
        }).catch(error => {
            this.setData({ quoteLoading: false, quoteError: true })
            wx.showToast({ title: error.message || '购物车加载失败', icon: 'none' })
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
        if (this.data.submitting) return
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
        if (this.data.submitting) return
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
        this.setData({ allPrice: allPrice.toFixed(2), cashPrice: cashPrice.toFixed(2), totalTicketNum, couponDiscount: '0.00', quoteLoading: true })
        return this.refreshQuote()
    },
    refreshQuote() {
        const sequence = (this.quoteSequence || 0) + 1
        this.quoteSequence = sequence
        this.setData({ quoteLoading: true, quoteError: false })
        return http.post('coupon/checkout', {
            userId: this.data.userInfo.id,
            userCouponId: this.data.selectedUserCouponId,
            lines: this.data.orderList.map(item => ({ commodityId: item.id, commodityNum: item.commodityNum, ticketNum: item.ticketNum }))
        }, false).then(r => {
            if (sequence !== this.quoteSequence) return false
            const quote = r.data
            const options = (quote.coupons || []).map(item => ({
                ...item,
                valueText: item.discountType === 'RATE' ? `${Number((Number(item.discountValue) * 10).toFixed(2))}折` : `减${Number(item.discountValue).toFixed(2)}元`,
                scopeText: item.couponType === 'ALL' ? '全场通用券' : '商品专用券 · 仅限指定商品',
                conditionText: Number(item.minSpend) > 0 ? `适用商品满${Number(item.minSpend)}元可用` : '无门槛',
                discountText: Number(item.discount || 0).toFixed(2)
            }))
            this.setData({
                allPrice: Number(quote.totalPrice).toFixed(2),
                ticketDiscount: Number(quote.ticketDiscount).toFixed(2),
                couponDiscount: Number(quote.couponDiscount).toFixed(2),
                cashPrice: Number(quote.payable).toFixed(2),
                selectedUserCouponId: quote.selectedUserCouponId || null,
                selectedCouponName: quote.couponName || '',
                couponOptions: options,
                availableCouponCount: options.filter(item => item.available).length,
                quoteLoading: false
            })
            if (quote.selectedCouponError) wx.showToast({ title: quote.selectedCouponError, icon: 'none' })
            return !quote.selectedCouponError
        }).catch(error => {
            if (sequence !== this.quoteSequence) return false
            this.setData({ quoteLoading: false, quoteError: true })
            wx.showToast({ title: error.message || '优惠计算失败，请重试', icon: 'none' })
            return false
        })
    },
    showCoupons() {
        if (!this.data.submitting) this.setData({ couponModal: true })
    },
    closeCoupons() { this.setData({ couponModal: false }) },
    preventClose() {},
    chooseCoupon(e) {
        if (this.data.submitting || this.data.quoteLoading) return
        const id = e.currentTarget.dataset.id
        const coupon = id ? this.data.couponOptions.find(item => String(item.userCouponId) === String(id)) : null
        if (id && (!coupon || !coupon.available)) return
        this.setData({ selectedUserCouponId: coupon ? coupon.userCouponId : null, couponModal: false })
        return this.refreshQuote()
    },
    couponCenter() { this.closeCoupons(); wx.navigateTo({ url: '/pages/coupon/index/index' }) },
    async submit() {
        if (this.data.submitting || this.data.quoteLoading) return
        if (!this.data.addressInfo || !this.data.orderList.length) {
            wx.showToast({ title: '请确认商品及默认收货地址', icon: 'none' })
            return
        }
        this.setData({ submitting: true })
        try {
            if (!await this.refreshQuote()) return
            wx.showLoading({ title: '正在提交订单', mask: true })
            if (this.data.orderType == 1) {
                const lines = this.data.orderList.map(item => ({
                    orderId: item.orderId,
                    commodityNum: item.commodityNum,
                    ticketNum: item.ticketNum
                }))
                await http.post('completeCart', { userId: this.data.userInfo.id, addressId: this.data.addressInfo.id, userCouponId: this.data.selectedUserCouponId, lines }, false)
            } else {
                let item = this.data.orderList[0]
                let data = {
                    commodityId: this.data.commodityId,
                    commodityNum: item.commodityNum,
                    ticketNum: item.ticketNum,
                    addressId: this.data.addressInfo.id,
                    userId: this.data.userInfo.id,
                    userCouponId: this.data.selectedUserCouponId
                }
                await http.post('buyGoods', data, false)
            }
            wx.hideLoading()
            this.orderSubmitted = true
            wx.showToast({ title: '下单成功', icon: 'success', duration: 1000 })
            // Keep the submit guard active until this page leaves.
            setTimeout(() => wx.navigateBack({ changed: true }), 1000)
        } catch (error) {
            wx.hideLoading()
            wx.showToast({ title: error.message || '下单失败，请重试', icon: 'none' })
            await this.refreshQuote()
        } finally {
            if (!this.orderSubmitted) this.setData({ submitting: false })
        }
    }
});
