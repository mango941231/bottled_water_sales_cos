<template>
  <div :class="[multipage === true ? 'multi-page':'single-page', 'not-menu-page', 'home-page']">
    <a-row :gutter="8" class="head-info" v-if="isPlatformAdmin">
      <a-card class="head-info-card">
        <a-col :span="12">
<!--          <div class="head-info-avatar">-->
<!--            <img alt="头像" :src="avatar">-->
<!--          </div>-->
          <div class="head-info-count">
            <div class="head-info-welcome">
              {{welcomeMessage}}
            </div>
            <div class="head-info-desc">
              <p>{{user.roleName ? user.roleName : '暂无角色'}}</p>
            </div>
            <div class="head-info-time">上次登录时间：{{user.lastLoginTime ? user.lastLoginTime : '第一次访问系统'}}</div>
          </div>
        </a-col>
        <a-col :span="12">
          <a-skeleton active v-if="loading" />
          <div v-if="!loading">
            <a-row class="more-info">
              <a-col :span="4"></a-col>
              <a-col :span="4"></a-col>
              <a-col :span="4"></a-col>
              <a-col :span="4">
                <head-info title="用户数量" :content="userNum" :center="false" :bordered="false"/>
              </a-col>
              <a-col :span="4">
                <head-info title="订单数量" :content="orderNum" :center="false" />
              </a-col>
              <a-col :span="4">
                <head-info title="商品总量" :content="commodityNum" :center="false" />
              </a-col>
            </a-row>
          </div>
        </a-col>
      </a-card>
    </a-row>
    <a-row :gutter="8" class="count-info" v-if="isPlatformAdmin">
      <a-col :span="12" class="visit-count-wrapper">
        <a-row :gutter="8">
          <a-col :span="12" v-show="false">
            <a-card class="visit-count">
              <a-skeleton active v-if="loading" />
              <apexchart v-show="!loading" ref="count" type=bar height=300 :options="chartOptions" :series="series" />
            </a-card>
          </a-col>
          <a-col :span="24">
            <a-card class="visit-count">
              <a-skeleton active v-if="loading" />
              <apexchart v-if="!loading && series1[0].data.length" type="line" height="300" :options="chartOptions1" :series="series1"></apexchart>
              <a-empty v-if="!loading && !series1[0].data.length" description="暂无订单数据" />
            </a-card>
          </a-col>
        </a-row>
      </a-col>
      <a-col :span="12" class="project-wrapper">
        <div style="background: #ECECEC; padding: 30px">
          <a-row :gutter="16">
            <a-col :span="8">
              <a-skeleton active v-if="loading" />
              <a-row :gutter="8" v-if="!loading">
                <a-col :span="24">
                  <a-card>
                    <p style="font-size: 14px;margin-left: 5px;margin-bottom: 8px;color: #aaaaaa">本月订单（单）</p>
                    <a-icon type="arrow-up" style="color: green;font-size: 20px;"/>
                    <span style="color: green;font-size: 20px;font-weight: bolder;margin-left: 5px">{{ orderMonthRevenue.orderNum }}</span> <span style="color: green;"> 单</span>
                  </a-card>
                </a-col>
                <a-col :span="24" style="margin-top: 60px">
                  <a-card>
                    <p style="font-size: 14px;margin-left: 5px;margin-bottom: 8px;color: #aaaaaa">本月收益（￥）</p>
                    <a-icon type="arrow-up" style="color: green;font-size: 20px;"/>
                    <span style="color: green;font-size: 20px;font-weight: bolder;margin-left: 5px">{{ orderMonthRevenue.orderPrice }}</span> <span style="color: green;"> 元</span>
                  </a-card>
                </a-col>
              </a-row>
            </a-col>
            <a-col :span="16">
              <a-skeleton active v-if="loading" />
              <apexchart v-if="!loading && hasRevenue" type="donut" height="265" :options="chartOptions3" :series="series3"></apexchart>
              <a-empty v-if="!loading && !hasRevenue" description="暂无收益数据" />
            </a-col>
          </a-row>
        </div>
      </a-col>
    </a-row>
    <a-card v-if="!isPlatformAdmin" :title="welcomeMessage">
      <p>欢迎使用桶装水配送销售平台，请从左侧菜单选择功能。</p>
    </a-card>
  </div>
</template>
<script>
import HeadInfo from '@/views/common/HeadInfo'
import {mapState} from 'vuex'
import moment from 'moment'
moment.locale('zh-cn')
const listData = []
for (let i = 0; i < 23; i++) {
  listData.push({
    href: 'https://www.antdv.com/',
    title: `ant design vue part ${i}`,
    avatar: 'https://zos.alipayobjects.com/rmsportal/ODTLcjxAfvqbxHnVXCYX.png',
    description:
      'Ant Design, a design language for background applications, is refined by Ant UED Team.',
    content:
      'We supply a series of design principles, practical patterns and high quality design resources (Sketch and Axure), to help people create their product prototypes beautifully and efficiently.'
  })
}
export default {
  name: 'HomePage',
  components: {HeadInfo},
  data () {
    return {
      series: [],
      chartOptions: {
        chart: {
          toolbar: {
            show: false
          }
        },
        plotOptions: {
          bar: {
            horizontal: false,
            columnWidth: '35%'
          }
        },
        dataLabels: {
          enabled: false
        },
        stroke: {
          show: true,
          width: 2,
          colors: ['transparent']
        },
        xaxis: {
          categories: []
        },
        fill: {
          opacity: 1

        }
      },
      series1: [{
        name: '交易额（元）',
        data: []
      }],
      chartOptions1: {
        chart: {
          type: 'line',
          height: 300
        },
        stroke: {
          curve: 'stepline'
        },
        dataLabels: {
          enabled: false
        },
        title: {
          text: '订单统计图',
          align: 'left'
        },
        markers: {
          hover: {
            sizeOffset: 4
          }
        },
        xaxis: {
          categories: []
        }
      },
      series2: [{
        name: '收益额',
        data: []
      }],
      chartOptions2: {
        chart: {
          type: 'bar',
          height: 400
        },
        plotOptions: {
          bar: {
            horizontal: false,
            columnWidth: '55%'
          }
        },
        dataLabels: {
          enabled: false
        },
        stroke: {
          show: true,
          width: 2,
          colors: ['transparent']
        },
        xaxis: {
          categories: []
        },
        fill: {
          opacity: 1
        },
        tooltip: {
          y: {
            formatter: function (val) {
              return '￥' + val + ' 元'
            }
          }
        }
      },
      series3: [],
      chartOptions3: {
        labels: [],
        chart: {
          type: 'donut'
        },
        responsive: [{
          breakpoint: 480,
          options: {
            chart: {
              width: 200
            },
            legend: {
              position: 'bottom'
            }
          }
        }]
      },
      todayIp: '',
      todayVisitCount: '',
      totalVisitCount: '',
      userRole: '',
      userDept: '',
      lastLoginTime: '',
      welcomeMessage: '',
      listData,
      pagination: {
        onChange: page => {
          console.log(page)
        },
        pageSize: 3
      },
      userNum: 0,
      orderNum: 0,
      commodityNum: 0,
      orderRevenueData: [],
      orderMonthRevenue: {
        orderNum: 0,
        orderPrice: 0
      },
      orderPriceRate: [],
      loading: true
    }
  },
  computed: {
    ...mapState({
      multipage: state => state.setting.multipage,
      user: state => state.account.user
    }),
    hasRevenue () {
      return this.series3.some(value => Number(value) > 0)
    },
    isPlatformAdmin () {
      return String(this.user.roleId || '').split(',').some(id => ['1', '74'].includes(id.trim()))
    },
    avatar () {
      return `static/avatar/${this.user.avatar}`
    }
  },
  methods: {
    welcome () {
      const date = new Date()
      const hour = date.getHours()
      let time = hour < 6 ? '早上好' : (hour <= 11 ? '上午好' : (hour <= 13 ? '中午好' : (hour <= 18 ? '下午好' : '晚上好')))
      return `${time}，${this.user.username}`
    },
    homeData () {
      this.loading = true
      this.$get('/cos/order-info/home').then((r) => {
        this.userNum = r.data.userNum
        this.orderNum = r.data.orderNum
        this.commodityNum = r.data.commodityNum
        let orderRevenueLabel = []
        let orderRevenueData = []
        r.data.orderRevenueData.forEach(item => {
          orderRevenueLabel.push(item.days)
          orderRevenueData.push(item.orderPrice)
        })
        this.series1[0].data = orderRevenueData
        this.chartOptions1.xaxis.categories = orderRevenueLabel
        this.orderMonthRevenue.orderNum = r.data.orderMonthRevenue.orderNum
        this.orderMonthRevenue.orderPrice = r.data.orderMonthRevenue.orderPrice
        let orderPriceRateLabel = []
        let orderPriceRateData = []
        r.data.orderPriceRate.forEach(item => {
          orderPriceRateLabel.push(item.name)
          orderPriceRateData.push(item.orderPrice)
        })
        this.series3 = orderPriceRateData
        this.chartOptions3.labels = orderPriceRateLabel
        setTimeout(() => {
          this.loading = false
        }, 500)
      }).catch(() => {
        this.loading = false
        this.$message.error('获取首页统计失败，请稍后重试')
      })
    }
  },
  mounted () {
    this.welcomeMessage = this.welcome()
    if (!this.isPlatformAdmin) return
    this.$get(`index/${this.user.username}`).then((r) => {
      let data = r.data.data
      this.todayIp = data.todayIp
      this.todayVisitCount = data.todayVisitCount
      this.totalVisitCount = data.totalVisitCount
      let sevenVisitCount = []
      let dateArr = []
      for (let i = 6; i >= 0; i--) {
        let time = moment().subtract(i, 'days').format('MM-DD')
        let contain = false
        for (let o of data.lastSevenVisitCount) {
          if (o.days === time) {
            contain = true
            sevenVisitCount.push(o.count)
          }
        }
        if (!contain) {
          sevenVisitCount.push(0)
        }
        dateArr.push(time)
      }
      let sevenUserVistCount = []
      for (let i = 6; i >= 0; i--) {
        let time = moment().subtract(i, 'days').format('MM-DD')
        let contain = false
        for (let o of data.lastSevenUserVisitCount) {
          if (o.days === time) {
            contain = true
            sevenUserVistCount.push(o.count)
          }
        }
        if (!contain) {
          sevenUserVistCount.push(0)
        }
      }
      if (this.$refs.count) {
        this.$refs.count.updateSeries([
          {
            name: '您',
            data: sevenUserVistCount
          },
          {
            name: '总数',
            data: sevenVisitCount
          }
        ], true)
      }
      if (this.$refs.count) {
        this.$refs.count.updateOptions({
          xaxis: {
            categories: dateArr
          },
          title: {
            text: '近七日系统访问记录',
            align: 'left'
          }
        }, true, true)
      }
      this.homeData()
    }).catch((r) => {
      console.error(r)
      // this.$message.error('获取首页信息失败')
    })
  }
}
</script>
<style lang="less">
  .home-page {
    .head-info {
      margin-bottom: .5rem;
      .head-info-card {
        padding: .5rem;
        border-color: #f1f1f1;
        .head-info-avatar {
          display: inline-block;
          float: left;
          margin-right: 1rem;
          img {
            width: 5rem;
            border-radius: 2px;
          }
        }
        .head-info-count {
          display: inline-block;
          float: left;
          .head-info-welcome {
            font-size: 1.05rem;
            margin-bottom: .1rem;
          }
          .head-info-desc {
            color: rgba(0, 0, 0, 0.45);
            font-size: .8rem;
            padding: .2rem 0;
            p {
              margin-bottom: 0;
            }
          }
          .head-info-time {
            color: rgba(0, 0, 0, 0.45);
            font-size: .8rem;
            padding: .2rem 0;
          }
        }
      }
    }
    .count-info {
      .visit-count-wrapper {
        padding-left: 0 !important;
        .visit-count {
          padding: .5rem;
          border-color: #f1f1f1;
          .ant-card-body {
            padding: .5rem 1rem !important;
          }
        }
      }
      .project-wrapper {
        padding-right: 0 !important;
        .project-card {
          border: none !important;
          .ant-card-head {
            border-left: 1px solid #f1f1f1 !important;
            border-top: 1px solid #f1f1f1 !important;
            border-right: 1px solid #f1f1f1 !important;
          }
          .ant-card-body {
            padding: 0 !important;
            table {
              width: 100%;
              td {
                width: 50%;
                border: 1px solid #f1f1f1;
                padding: .6rem;
                .project-avatar-wrapper {
                  display:inline-block;
                  float:left;
                  margin-right:.7rem;
                  .project-avatar {
                    color: #42b983;
                    background-color: #d6f8b8;
                  }
                }
              }
            }
          }
          .project-detail {
            display:inline-block;
            float:left;
            text-align:left;
            width: 78%;
            .project-name {
              font-size:.9rem;
              margin-top:-2px;
              font-weight:600;
            }
            .project-desc {
              color:rgba(0, 0, 0, 0.45);
              p {
                margin-bottom:0;
                font-size:.6rem;
                white-space:normal;
              }
            }
          }
        }
      }
    }
  }
</style>
