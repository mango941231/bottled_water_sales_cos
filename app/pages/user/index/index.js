// pages/user/index.js
var app = getApp();
var isLoginSuccess = false;
let http = require('../../../utils/request')

Page({
  data: {
    userInfo: {},//默认为空，但是进入页面我们需要先请求后台接口判断用户是否已经授权过信息，没有获取到的情况下我们需要提醒新用户授权
    hasUserInfo: false,//判断是否展示授权按钮/授权弹窗或者跳到授权页面
    canIUseGetUserProfile: false,//是否支持wx.getUserProfile方法
    starCount: 0,
    forksCount: 0,
    visitTotal: 0,
  },
  onLoad: function (options) {
    //进入页面判断是否可以使用wx.getUserProfile
    if (wx.canIUse('getUserProfile')) {
      this.setData({
        canIUseGetUserProfile: true
      })
    }
    this.loginTap()
    let that = this;
    wx.showLoading({
      title: '数据加载中',
      mask: true,
    })
    let i = 0;
    numDH();
    function numDH() {
      if (i < 20) {
        setTimeout(function () {
          that.setData({
            visitTotal: i,
            forksCount: i,
            visitTotal: i
          })
          i++
          numDH();
        }, 20)
      } else {
        that.setData({
          starCount: that.coutNum(999),
          forksCount: that.coutNum(8888),
          visitTotal: that.coutNum(77777)
        })
      }
    }
    wx.hideLoading()
  },
  loginTap() {
    wx.getStorage({
      key: 'userInfo',
      success: (res) => {
        app.globalData.userInfo = res.data
        this.initLoginMsg()
      }
    })
  },
  getUserProfile(e) {
    // 推荐使用wx.getUserProfile获取用户信息，开发者每次通过该接口获取用户个人信息均需用户确认
    // 开发者妥善保管用户快速填写的头像昵称，避免重复弹窗
    wx.showLoading({
      title: '正在登录...',
    })
    wx.getUserProfile({
      desc: '用于完善会员资料', // 声明获取用户个人信息后的用途，后续会展示在弹窗中，请谨慎填写
      success: (res) => this.loginWithProfile(res.userInfo),
      fail: (err) => {
        wx.hideLoading()
        wx.showToast({ title: err.errMsg || '用户授权失败', icon: 'none' })
      }
    })
  },
  loginWithProfile(userInfo) {
    wx.login({
      success: (loginResult) => {
        if (!loginResult.code) {
          wx.hideLoading()
          wx.showToast({ title: loginResult.errMsg || '微信登录失败', icon: 'none' })
          return
        }
        http.post('userAdd', {
          userName: userInfo.nickName,
          avatar: userInfo.avatarUrl,
          sex: userInfo.gender == 0 ? 1 : 2,
          openId: loginResult.code
        }).then((result) => {
          this.setData({
            userInfo: result.data,
            hasUserInfo: true
          })
          app.globalData.userInfo = result.data
          wx.setStorage({ key: 'userInfo', data: result.data })
          if (app.userInfoReadyCallback) {
            app.userInfoReadyCallback(result)
          }
        }).catch((err) => {
          console.error('小程序登录失败', err)
          wx.showToast({ title: err.message || '登录失败，请检查小程序 AppID 和后端配置', icon: 'none' })
        }).then(() => {
          wx.hideLoading()
        })
      },
      fail: (err) => {
        wx.hideLoading()
        wx.showToast({ title: err.errMsg || '微信登录失败', icon: 'none' })
      }
    })
  },
  initLoginMsg() {
    if (app.globalData.userInfo) {
      isLoginSuccess = true;
      this.setData({
        userInfo: app.globalData.userInfo,
        hasUserInfo: true
      })
    }
  },
  getUserInfo(e) {
    // 不推荐使用getUserInfo获取用户信息，预计自2021年4月13日起，getUserInfo将不再弹出弹窗，并直接返回匿名的用户个人信息
    if (e.detail.userInfo) {
      wx.showLoading({ title: '正在登录...' })
      this.loginWithProfile(e.detail.userInfo)
    }
  },
  coutNum(e) {
    if (e > 1000 && e < 10000) {
      e = (e / 1000).toFixed(1) + 'k'
    }
    if (e > 10000) {
      e = (e / 10000).toFixed(1) + 'W'
    }
    return e
  },
  CopyLink(e) {

  },
  showModal(e) {
    this.setData({
      modalName: e.currentTarget.dataset.target
    })
  },
  hideModal(e) {
    this.setData({
      modalName: null
    })
  },
  showQrcode() {

  },

});
