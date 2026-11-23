<template>
  <a-modal :visible="orderShow" title="订单详情" :width="1000" :style="{maxWidth: 'calc(100vw - 32px)'}" @cancel="onClose">
    <template slot="footer">
      <a-button @click="onClose">关闭</a-button>
    </template>
    <a-spin :spinning="loading">
      <div v-if="orderData" class="order-detail">
        <h3>订单信息</h3>
        <a-row :gutter="24">
          <a-col :sm="8" :xs="24"><b>订单编号：</b>{{ detail.code || '- -' }}</a-col>
          <a-col :sm="8" :xs="24"><b>下单时间：</b>{{ detail.createDate || '- -' }}</a-col>
          <a-col :sm="8" :xs="24"><b>订单状态：</b>{{ statusLabel }}</a-col>
          <a-col :sm="8" :xs="24"><b>订单价格：</b>￥{{ money(detail.orderPrice) }}</a-col>
          <a-col :sm="8" :xs="24"><b>使用水票数量：</b>{{ detail.ticketNum || 0 }} 张</a-col>
          <a-col :sm="8" :xs="24"><b>本次购买数量：</b>{{ detail.commodityNum || 1 }} 桶</a-col>
          <a-col :span="24"><b>剩余水票（本商品）：</b>{{ detail.ticketRemaining == null ? '- -' : detail.ticketRemaining + ' 张' }}
            <span class="hint">当前可用余额，不含已过期或已停用水票</span>
          </a-col>
        </a-row>
        <h3>购买及收货信息</h3>
        <a-row :gutter="24">
          <a-col :sm="8" :xs="24"><b>购买人：</b>{{ detail.userName || '- -' }}</a-col>
          <a-col :sm="8" :xs="24"><b>收货人：</b>{{ detail.taskUserName || '- -' }}</a-col>
          <a-col :sm="8" :xs="24"><b>联系方式：</b>{{ detail.phone || '- -' }}</a-col>
          <a-col :span="24"><b>收货地址：</b>{{ detail.address || '- -' }}</a-col>
        </a-row>
        <h3>商品信息</h3>
        <a-row :gutter="24">
          <a-col :sm="8" :xs="24"><b>商品编号：</b>{{ detail.commodityCode || '- -' }}</a-col>
          <a-col :sm="8" :xs="24"><b>商品名称：</b>{{ detail.name || '- -' }}</a-col>
          <a-col :sm="8" :xs="24"><b>商品类型：</b>{{ detail.typeName || '- -' }}</a-col>
          <a-col :sm="8" :xs="24"><b>上架状态：</b>{{ detail.onPut == 1 ? '上架中' : '下架中' }}</a-col>
          <a-col :span="24"><b>商品介绍：</b>{{ detail.content || '- -' }}</a-col>
        </a-row>
        <div class="pictures">
          <button v-for="(url, index) in imageUrls" :key="index" type="button" @click="previewImage = url" aria-label="预览商品图片">
            <img :src="url" alt="商品图片" />
          </button>
        </div>
        <h3>订单评价</h3>
        <a-list item-layout="horizontal" :data-source="replyList" :locale="{emptyText: '暂无评价'}">
          <a-list-item slot="renderItem" slot-scope="item">
            <a-comment :author="item.userName" :avatar="imageUrl(item.avatar)" :content="item.content" :datetime="item.createDate" />
          </a-list-item>
        </a-list>
      </div>
    </a-spin>
    <a-modal :visible="!!previewImage" :footer="null" @cancel="previewImage = ''">
      <img alt="商品图片预览" style="width: 100%" :src="previewImage" />
    </a-modal>
  </a-modal>
</template>

<script>
import {orderStatusLabel} from '@/utils/order-status'
export default {
  name: 'OrderDetail',
  props: {
    orderShow: {type: Boolean, default: false},
    orderData: {type: Object, default: null}
  },
  data () {
    return {summary: null, loading: false, replyList: [], previewImage: '', loadVersion: 0}
  },
  computed: {
    detail () {
      return {...this.orderData, ...this.summary}
    },
    statusLabel () {
      return orderStatusLabel(this.detail.orderStatus)
    },
    imageUrls () {
      return (this.detail.images || '').split(',').filter(Boolean).map(this.imageUrl)
    }
  },
  watch: {
    orderShow (value) {
      if (value && this.orderData) this.loadDetail()
      else this.loadVersion++
    }
  },
  methods: {
    loadDetail () {
      const version = ++this.loadVersion
      const order = this.orderData
      this.summary = null
      this.replyList = []
      this.previewImage = ''
      this.loading = true
      this.$get(`/cos/order-info/order/detail/${encodeURIComponent(order.code)}`).then(r => {
        if (version === this.loadVersion) this.summary = r.data.summary
      }).finally(() => {
        if (version === this.loadVersion) this.loading = false
      })
      this.$get('/cos/evaluation/getEvaluationByOrderId', {id: order.id}).then(r => {
        if (version === this.loadVersion) this.replyList = r.data.data || []
      })
    },
    money (value) {
      return value == null ? '- -' : Number(value).toFixed(2)
    },
    imageUrl (value) {
      if (!value || /^(https?:\/\/|\/)/.test(value)) return value
      return '/febs/imagesWeb/' + value
    },
    onClose () {
      this.$emit('close')
    }
  }
}
</script>

<style scoped>
.order-detail { padding: 0 8px; }
.order-detail h3 { margin: 20px 0 16px; font-weight: 600; }
.order-detail .ant-col { margin-bottom: 16px; overflow-wrap: break-word; }
.hint { display: inline-block; margin-left: 12px; color: #888; font-size: 12px; }
.pictures { display: flex; flex-wrap: wrap; gap: 12px; }
.pictures button { padding: 4px; border: 1px solid #e8e8e8; background: white; cursor: pointer; }
.pictures img { width: 100px; height: 100px; object-fit: cover; }
</style>
