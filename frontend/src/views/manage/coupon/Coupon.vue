<template>
  <a-card :bordered="false" class="card-area">
    <a-form layout="inline" class="search-form" @submit.prevent="search">
      <a-form-item label="优惠券名称"><a-input v-model="query.name" placeholder="输入名称查询" @pressEnter="search" /></a-form-item>
      <a-form-item label="适用类型">
        <a-select v-model="query.couponType" style="width: 160px">
          <a-select-option value="">全部类型</a-select-option>
          <a-select-option value="ALL">全场通用券</a-select-option>
          <a-select-option value="PRODUCT">商品专用券</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="状态">
        <a-select v-model="query.status" style="width: 120px">
          <a-select-option value="">全部状态</a-select-option>
          <a-select-option :value="1">已启用</a-select-option>
          <a-select-option :value="0">已停用</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item><a-button type="primary" @click="search">查询</a-button><a-button class="reset-button" @click="reset">重置</a-button></a-form-item>
    </a-form>
    <div class="operator"><a-button type="primary" icon="plus" @click="create">新增优惠券</a-button></div>
    <a-table :columns="columns" :data-source="coupons" :row-key="row => row.id" :pagination="pagination" :loading="loading" :scroll="{ x: 1640 }" size="middle" @change="changePage">
      <template slot="name" slot-scope="value, row"><a @click="view(row)">{{ value }}</a></template>
      <template slot="type" slot-scope="value"><a-tag :color="value === 'ALL' ? 'blue' : 'purple'">{{ scopeName(value) }}</a-tag></template>
      <template slot="discount" slot-scope="value, row">{{ discountText(row) }}</template>
      <template slot="products" slot-scope="value, row"><span class="product-names">{{ row.couponType === 'ALL' ? '全场商品' : value || '暂无关联商品' }}</span></template>
      <template slot="activity" slot-scope="value, row"><div>{{ row.startTime || '不限开始时间' }}</div><div>至 {{ row.endTime || '不限结束时间' }}</div></template>
      <template slot="status" slot-scope="value"><a-tag :color="value === 1 ? 'green' : 'default'">{{ value === 1 ? '已启用' : '已停用' }}</a-tag></template>
      <template slot="actions" slot-scope="value, row">
        <a @click="view(row)">详情</a><a-divider type="vertical" /><a @click="edit(row)">编辑</a><a-divider type="vertical" />
        <a-popconfirm title="确定删除此优惠券？将停止领取，保留用户领取记录。" ok-text="删除" cancel-text="取消" @confirm="remove(row)"><a class="delete-action">删除</a></a-popconfirm>
      </template>
    </a-table>
    <coupon-editor :visible="editorVisible" :coupon="editing" @close="editorVisible = false" @saved="saved" />
    <a-modal title="优惠券详情" :visible="detailVisible" :footer="null" width="700px" @cancel="detailVisible = false">
      <a-spin :spinning="detailLoading">
        <dl v-if="detail" class="coupon-details">
          <dt>优惠券名称</dt><dd>{{ detail.name }}</dd>
          <dt>适用类型</dt><dd>{{ scopeName(detail.couponType) }}</dd>
          <dt>适用商品</dt><dd>{{ detail.couponType === 'ALL' ? '全场商品' : detail.commodityNames || '暂无关联商品' }}</dd>
          <dt>优惠内容</dt><dd>{{ discountText(detail) }}</dd>
          <dt>使用门槛</dt><dd>{{ Number(detail.minSpend) ? `满 ¥${detail.minSpend} 可用` : '无门槛' }}</dd>
          <dt>领取后有效期</dt><dd>{{ detail.validDays }} 天（不超过活动结束时间）</dd>
          <dt>活动开始时间</dt><dd>{{ detail.startTime || '不限' }}</dd>
          <dt>活动结束时间</dt><dd>{{ detail.endTime || '不限' }}</dd>
          <dt>已领取 / 总量</dt><dd>{{ detail.claimedCount || 0 }} / {{ detail.totalCount == null ? '不限量' : detail.totalCount }}</dd>
          <dt>状态</dt><dd>{{ detail.status === 1 ? '已启用' : '已停用' }}</dd>
          <dt>创建时间</dt><dd>{{ detail.createTime }}</dd>
        </dl>
      </a-spin>
    </a-modal>
  </a-card>
</template>

<script>
import CouponEditor from './CouponEditor'

const emptyQuery = () => ({ name: '', couponType: '', status: '' })

export default {
  name: 'Coupon',
  components: { CouponEditor },
  data () {
    return {
      query: emptyQuery(),
      activeQuery: emptyQuery(),
      loading: false,
      requestSequence: 0,
      coupons: [],
      editorVisible: false,
      editing: null,
      detailVisible: false,
      detailLoading: false,
      detail: null,
      pagination: { current: 1, pageSize: 10, total: 0, showSizeChanger: true, showQuickJumper: true, pageSizeOptions: ['10', '20', '50', '100'], showTotal: total => `共 ${total} 条` },
      columns: [
        { title: '优惠券名称', dataIndex: 'name', width: 180, scopedSlots: { customRender: 'name' } },
        { title: '适用类型', dataIndex: 'couponType', width: 140, scopedSlots: { customRender: 'type' } },
        { title: '优惠内容', dataIndex: 'discountValue', width: 130, scopedSlots: { customRender: 'discount' } },
        { title: '使用门槛', dataIndex: 'minSpend', width: 120, customRender: value => Number(value) ? `满 ¥${value}` : '无门槛' },
        { title: '适用商品', dataIndex: 'commodityNames', width: 230, scopedSlots: { customRender: 'products' } },
        { title: '领取后有效期', dataIndex: 'validDays', width: 130, customRender: value => `${value} 天` },
        { title: '活动时间', key: 'activity', width: 200, scopedSlots: { customRender: 'activity' } },
        { title: '已领取 / 总量', key: 'quantity', width: 150, customRender: (value, row) => `${row.claimedCount || 0} / ${row.totalCount == null ? '不限' : row.totalCount}` },
        { title: '状态', dataIndex: 'status', width: 100, scopedSlots: { customRender: 'status' } },
        { title: '操作', key: 'actions', width: 180, fixed: 'right', scopedSlots: { customRender: 'actions' } }
      ]
    }
  },
  mounted () { this.load() },
  methods: {
    scopeName (value) { return value === 'ALL' ? '全场通用券' : '商品专用券' },
    discountText (row) { return row.discountType === 'RATE' ? `${Number((Number(row.discountValue) * 10).toFixed(2))} 折` : `立减 ¥${row.discountValue}` },
    load () {
      const sequence = ++this.requestSequence
      this.loading = true
      return this.$get('/cos/coupon/admin/page', {
        current: this.pagination.current,
        size: this.pagination.pageSize,
        name: encodeURIComponent(this.activeQuery.name.trim()),
        couponType: this.activeQuery.couponType,
        status: this.activeQuery.status === '' ? null : this.activeQuery.status
      }).then(result => {
        if (sequence !== this.requestSequence) return
        this.coupons = result.data.data.records || []
        this.pagination = { ...this.pagination, total: result.data.data.total }
      }).catch(() => {}).finally(() => {
        if (sequence === this.requestSequence) this.loading = false
      })
    },
    search () { this.activeQuery = { ...this.query }; this.pagination.current = 1; this.load() },
    reset () { this.query = emptyQuery(); this.search() },
    changePage (pagination) { this.pagination = { ...this.pagination, current: pagination.current, pageSize: pagination.pageSize }; this.load() },
    create () { this.editing = null; this.editorVisible = true },
    edit (row) {
      this.$get(`/cos/coupon/admin/${row.id}`).then(result => {
        this.editing = result.data.data
        this.editorVisible = true
      }).catch(() => {})
    },
    view (row) {
      this.detail = null
      this.detailVisible = true
      this.detailLoading = true
      this.$get(`/cos/coupon/admin/${row.id}`).then(result => { this.detail = result.data.data })
        .catch(() => { this.detailVisible = false }).finally(() => { this.detailLoading = false })
    },
    saved () { this.editorVisible = false; this.$message.success('优惠券已保存'); this.load() },
    remove (row) {
      this.$delete(`/cos/coupon/admin/${row.id}`).then(() => {
        this.$message.success('优惠券已删除')
        if (this.coupons.length === 1 && this.pagination.current > 1) this.pagination.current--
        this.load()
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
.search-form { margin-bottom: 16px; }
.operator { margin-bottom: 16px; }
.reset-button { margin-left: 8px; }
.product-names { white-space: normal; word-break: break-all; }
.delete-action { color: #f5222d; }
.coupon-details { display: grid; grid-template-columns: 140px minmax(0, 1fr); gap: 16px; margin: 0; }
.coupon-details dt { color: #8a9299; }
.coupon-details dd { margin: 0; word-break: break-all; }
</style>
