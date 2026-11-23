const app = getApp();
let http = require('../../../utils/request')
Page({
	data: {
		StatusBar: app.globalData.StatusBar,
		imageBaseUrl: app.globalData.imageBaseUrl,
		CustomBar: app.globalData.CustomBar,
		hidden: true,
		current: 0,lines: 0,
		swiperlist: [{
			id: 0,
			url: 'https://www.crbeverage.com/indexBanner/2023-07-20/583fd836-c200-4311-9ef7-9d2e8dece839.jpg',
			type: 1
		}, {
			id: 1,
			url: 'https://www.crbeverage.com/ybPorduct/f1225d1d-77a5-4891-9cec-6711429349ad.png',
			type: 2
		}],
		iconList: [{
			id: 1,
			iconClass: 'icon-ticket',
			name: '水票中心',
			type: 1
		}, {
			id: 2,
			iconClass: 'icon-redpacket_fill',
			name: '优惠中心',
			type: 2
		}],
		commodityHot: [],
		keys: '',
		videosrc: "http://wxsnsdy.tc.qq.com/105/20210/snsdyvideodownload?filekey=30280201010421301f0201690402534804102ca905ce620b1241b726bc41dcff44e00204012882540400&bizid=1023&hy=SH&fileparam=302c020101042530230204136ffd93020457e3c4ff02024ef202031e8d7f02030f42400204045a320a0201000400",

	},
	onLoad: function () {
		/*console.log(app.globalData.StatusBar);
		console.log(app.globalData.CustomBar);*/
	    // wx.getSetting({
	    //     success: res => {
		//         if (!res.authSetting['scope.userInfo']) {
		//             wx.redirectTo({
		//               	url: '/pages/auth/auth'
		//             })
		//         }
	    //     }
	    // });
		this.home()
	},
	onProductImageError(e) {
		const id = e.currentTarget.dataset.id
		this.setData({
			commodityHot: this.data.commodityHot.map(item => item.id == id ? Object.assign({}, item, { imageUrl: '' }) : item)
		})
	},
	home() {
		http.get('home').then((r) => {
			const products = Array.isArray(r.commodityHot) ? r.commodityHot : []
			products.forEach(item => {
				const image = item.images || item.image || ''
				const firstImage = (Array.isArray(image) ? image : String(image).split(','))[0]
				item.imageUrl = this.resolveImageUrl(firstImage)
			});
			this.setData({
				commodityHot: products
			})
		}).catch(() => this.setData({ commodityHot: [] }))
	},
	resolveImageUrl(path) {
		if (!path) return ''
		const value = String(path).trim()
		if (/^(https?:)?\/\//i.test(value)) return value
		return this.data.imageBaseUrl + value.replace(/^\/?(?:febs\/)?imagesWeb\//i, '').replace(/^\/+/, '')
	},
	swiperchange: function (e) {
		this.setData({
			current:e.detail.current
		});
	},
	swipclick: function (e) {
		let that = this;
		var swip = that.data.swiperlist[that.data.current];
		console.log(swip);
		if (swip.type === 1) {
			wx.navigateTo({
				url: '/pages/home/doc/index?id=' + swip.id
			});
		}
	},
	itemckcred: function (e) {
		let that = this;
		var item = e.currentTarget.dataset;
		console.log(item.index,item.itemtype)
		if (item.itemtype === 1) {
			wx.navigateTo({ url: '/pages/water-ticket/index' });
		}
		if (item.itemtype === 2) {
			wx.navigateTo({
				url: '/pages/coupon/index/index'
			});
		}
	},
	getKeysValue: function (e) {
		this.setData({ keys: e.detail.value })
	},
	search: function () {
		wx.navigateTo({
			url: '/pages/home/search/index?key='+this.data.keys+''
		});
	}
});
