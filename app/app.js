//app.js
const config = require('./config');

App({
    onLaunch: function () {
        if (config.MOCK_LOGIN) {
            const userInfo = Object.assign({}, config.MOCK_USER)
            this.globalData.userInfo = userInfo
            wx.setStorageSync('userInfo', userInfo)
        }
        wx.getSystemInfo({
            success: e => {
                this.globalData.StatusBar = e.statusBarHeight;
                let custom = wx.getMenuButtonBoundingClientRect();
                this.globalData.Custom = custom;
                let CustomBar = custom.bottom + custom.top - e.statusBarHeight;
                this.globalData.CustomBar = CustomBar;
                //适配全面屏底部距离
                if (CustomBar > 75) {
                    this.globalData.tabbar_bottom = "y"
                }
            }
        })
    },
    globalData: {
        userInfo: null,
        serverOrigin: config.SERVER_ORIGIN,
        imageBaseUrl: config.IMAGE_BASE_URL,
        uploadUrl: config.UPLOAD_URL
    }
});

