<template>
  <a-modal :visible="userShow" title="用户详情" :width="1100" :style="{maxWidth: 'calc(100vw - 32px)'}" @cancel="onClose">
    <template slot="footer"><a-button @click="onClose">关闭</a-button></template>
    <template v-if="userData">
      <p><b>用户：</b>{{ userData.userName }} <span class="user-code">{{ userData.code }}</span></p>
      <a-tabs default-active-key="tickets">
        <a-tab-pane key="tickets" tab="拥有的水票">
          <a-alert :message="`当前可用水票共 ${availableTotal} 张；下表包含全部已购水票。`" type="info" show-icon class="ticket-summary" />
          <a-table :columns="ticketColumns" :data-source="tickets" :row-key="row => row.id" :loading="loading" :scroll="{x: 1040}" :pagination="{pageSize: 8}" size="small">
            <template slot="ticketState" slot-scope="text, row"><a-tag :color="ticketState(row).color">{{ ticketState(row).label }}</a-tag></template>
          </a-table>
        </a-tab-pane>
        <a-tab-pane key="orders" tab="订单记录">
          <a-table :columns="orderColumns" :data-source="orders" :row-key="row => row.id" :loading="loading" :scroll="{x: 900}" :pagination="{pageSize: 8}" size="small" />
        </a-tab-pane>
      </a-tabs>
    </template>
  </a-modal>
</template>

<script>
import moment from 'moment'
import {orderStatusLabel} from '@/utils/order-status'

export default {
  name: 'UserView',
  props: {userShow: Boolean, userData: {type: Object, default: null}},
  data () {
    const date = value => value ? moment(value).format('YYYY-MM-DD HH:mm:ss') : '- -'
    return {
      tickets: [],
      orders: [],
      loading: false,
      loadVersion: 0,
      ticketColumns: [
        {title: '套餐名称', dataIndex: 'packageName', width: 160},
        {title: '关联商品', dataIndex: 'commodityName', width: 150},
        {title: '总数量', dataIndex: 'totalNum', width: 90},
        {title: '剩余数量', dataIndex: 'remainingNum', width: 90},
        {title: '购买金额', dataIndex: 'purchasePrice', width: 100, customRender: value => `￥${Number(value || 0).toFixed(2)}`},
        {title: '购买时间', dataIndex: 'createTime', width: 180, customRender: date},
        {title: '到期时间', dataIndex: 'expireTime', width: 180, customRender: date},
        {title: '状态', key: 'state', width: 90, scopedSlots: {customRender: 'ticketState'}}
      ],
      orderColumns: [
        {title: '下单时间', dataIndex: 'createDate', width: 180},
        {title: '商品名称', dataIndex: 'name', width: 180},
        {title: '购买数量', dataIndex: 'commodityNum', width: 100},
        {title: '订单价格', dataIndex: 'orderPrice', width: 120, customRender: value => `￥${Number(value || 0).toFixed(2)}`},
        {title: '使用水票', dataIndex: 'ticketNum', width: 100},
        {title: '订单状态', dataIndex: 'orderStatus', width: 120, customRender: orderStatusLabel}
      ]
    }
  },
  computed: {
    availableTotal () {
      return this.tickets.filter(row => this.ticketState(row).label === '可使用').reduce((sum, row) => sum + Number(row.remainingNum || 0), 0)
    }
  },
  watch: {
    userShow (value) {
      if (value && this.userData) this.load()
      else this.loadVersion++
    }
  },
  methods: {
    ticketState (row) {
      if (String(row.status) !== '1') return {label: '已停用', color: 'default'}
      if (row.expired || moment(row.expireTime).isSameOrBefore(moment())) return {label: '已过期', color: 'red'}
      if (Number(row.remainingNum) <= 0) return {label: '已用完', color: 'default'}
      return {label: '可使用', color: 'green'}
    },
    load () {
      const version = ++this.loadVersion
      const userId = this.userData.id
      this.tickets = []
      this.orders = []
      this.loading = true
      Promise.all([
        this.$get(`/cos/water-ticket/admin/accounts/${userId}`),
        this.$get('/cos/order-info/orderListByUserId', {userId})
      ]).then(([tickets, orders]) => {
        if (version !== this.loadVersion) return
        this.tickets = tickets.data.data || []
        this.orders = orders.data.data || []
      }).catch(() => {
        if (version === this.loadVersion) this.$message.error('用户详情加载失败，请重新打开')
      }).finally(() => {
        if (version === this.loadVersion) this.loading = false
      })
    },
    onClose () { this.$emit('close') }
  }
}
</script>

<style scoped>
.user-code { margin-left: 12px; color: #888; }
.ticket-summary { margin-bottom: 16px; }
</style>
