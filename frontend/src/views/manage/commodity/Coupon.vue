<template>
  <a-card :bordered="false" class="card-area">
    <div class="operator"><a-button type="primary" @click="create">新增优惠券</a-button></div>
    <a-table :columns="columns" :data-source="coupons" :row-key="row => row.id" :pagination="false" size="middle">
      <template slot="type" slot-scope="value">{{ value === 'ALL' ? '全场通用券' : '指定商品券' }}</template>
      <template slot="discount" slot-scope="value, row">{{ row.discountType === 'RATE' ? `${Number(value) * 10}折` : `立减 ¥${value}` }}</template>
      <template slot="status" slot-scope="value"><a-tag :color="value ? 'green' : 'default'">{{ value ? '领取中' : '已停用' }}</a-tag></template>
      <template slot="actions" slot-scope="value, row">
        <a @click="edit(row)">编辑</a><a-divider type="vertical" />
        <a-popconfirm title="停用该优惠券？已领取的优惠券不会删除。" @confirm="disable(row)"><a>停用</a></a-popconfirm>
      </template>
    </a-table>
    <a-modal :visible="visible" :title="form.id ? '编辑优惠券' : '新增优惠券'" :confirm-loading="saving" @ok="save" @cancel="visible = false" width="760px">
      <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 17 }">
        <a-form-item label="优惠券名称"><a-input v-model="form.name" placeholder="例如：新用户满减券" /></a-form-item>
        <a-form-item label="适用范围">
          <a-radio-group v-model="form.couponType">
            <a-radio value="ALL">全场通用券</a-radio><a-radio value="PRODUCT">指定商品券</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="form.couponType === 'PRODUCT'" label="适用商品">
          <a-select v-model="form.commodityIds" mode="multiple" placeholder="可选择多个商品" style="width: 100%">
            <a-select-option v-for="item in commodities" :key="item.id" :value="item.id">{{ item.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="优惠方式">
          <a-radio-group v-model="form.discountType"><a-radio value="AMOUNT">立减金额</a-radio><a-radio value="RATE">折扣</a-radio></a-radio-group>
          <a-input-number v-model="form.discountValue" :min="0.01" :max="form.discountType === 'RATE' ? 0.99 : 99999" :step="form.discountType === 'RATE' ? 0.05 : 1" :precision="2" />
          <span class="unit-label">{{ form.discountType === 'RATE' ? '折扣系数，如 0.9 表示 9 折' : '元' }}</span>
        </a-form-item>
        <a-form-item label="使用门槛"><a-input-number v-model="form.minSpend" :min="0" :precision="2" /> <span class="unit-label">元，0 为无门槛</span></a-form-item>
        <a-form-item label="领取后有效期"><a-input-number v-model="form.validDays" :min="1" :precision="0" /> <span class="unit-label">天</span></a-form-item>
        <a-form-item label="可领取数量"><a-input-number v-model="form.totalCount" :min="1" :precision="0" placeholder="不限量" /> <span class="unit-label">留空表示不限量</span></a-form-item>
        <a-form-item label="活动时间">
          <a-input v-model="form.startTime" placeholder="YYYY-MM-DD HH:mm:ss" style="width: 190px" />
          <span class="unit-label">至</span>
          <a-input v-model="form.endTime" placeholder="YYYY-MM-DD HH:mm:ss" style="width: 190px" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script>
const emptyForm = () => ({ couponType: 'ALL', discountType: 'AMOUNT', discountValue: 5, minSpend: 0, validDays: 30, totalCount: undefined, startTime: null, endTime: null, commodityIds: [], status: 1 })

export default {
  name: 'Coupon',
  data () {
    return {
      visible: false,
      saving: false,
      form: emptyForm(),
      coupons: [],
      commodities: [],
      columns: [
        { title: '优惠券', dataIndex: 'name' },
        { title: '类型', dataIndex: 'couponType', scopedSlots: { customRender: 'type' } },
        { title: '优惠', dataIndex: 'discountValue', scopedSlots: { customRender: 'discount' } },
        { title: '门槛', dataIndex: 'minSpend', customRender: value => Number(value) ? `满 ¥${value}` : '无门槛' },
        { title: '适用商品', dataIndex: 'commodityNames', ellipsis: true, customRender: value => value || '全场商品' },
        { title: '领取量', customRender: (value, row) => `${row.claimedCount || 0} / ${row.totalCount || '不限'}` },
        { title: '状态', dataIndex: 'status', scopedSlots: { customRender: 'status' } },
        { title: '操作', scopedSlots: { customRender: 'actions' } }
      ]
    }
  },
  mounted () { this.load() },
  methods: {
    load () {
      Promise.all([this.$get('/cos/coupon/admin/list'), this.$get('/cos/commodity-info/list')]).then(([couponResult, commodityResult]) => {
        this.coupons = couponResult.data.data || []
        this.commodities = commodityResult.data.data || []
      })
    },
    create () { this.form = emptyForm(); this.visible = true },
    edit (row) { this.form = { ...emptyForm(), ...row, commodityIds: row.commodityIds || [] }; this.visible = true },
    save () {
      if (!this.form.name || !this.form.discountValue || !this.form.validDays || (this.form.couponType === 'PRODUCT' && !this.form.commodityIds.length)) {
        this.$message.warning('请填写完整的优惠券信息'); return
      }
      this.saving = true
      this.$post('/cos/coupon/admin', { ...this.form, commodityIds: this.form.commodityIds.join(',') }).then(() => {
        this.$message.success('优惠券已保存'); this.visible = false; this.load()
      }).finally(() => { this.saving = false })
    },
    disable (row) {
      this.$delete(`/cos/coupon/admin/${row.id}`).then(() => { this.$message.success('优惠券已停用'); this.load() })
    }
  }
}
</script>

<style scoped>
.operator { margin-bottom: 16px; }
.unit-label { margin: 0 8px; color: #8a9299; }
</style>
