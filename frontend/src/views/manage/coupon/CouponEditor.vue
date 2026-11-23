<template>
  <a-modal :visible="visible" :title="coupon ? '编辑优惠券' : '新增优惠券'" :confirm-loading="saving" :mask-closable="false" width="780px" @ok="save" @cancel="close">
    <a-form :label-col="{ span: 5 }" :wrapper-col="{ span: 18 }">
      <a-form-item label="优惠券名称" required><a-input v-model="form.name" :max-length="100" placeholder="例如：新用户满减券" /></a-form-item>
      <a-form-item label="适用类型" required>
        <a-radio-group v-model="form.couponType"><a-radio value="ALL">全场通用券</a-radio><a-radio value="PRODUCT">商品专用券</a-radio></a-radio-group>
      </a-form-item>
      <a-form-item v-if="form.couponType === 'PRODUCT'" label="适用商品" required>
        <a-select v-model="form.commodityIds" mode="multiple" :loading="goodsLoading" :filter-option="filterProduct" placeholder="可搜索并选择多个商品" style="width: 100%">
          <a-select-option v-for="item in productOptions" :key="item.id" :value="item.id" :disabled="item.deleted">{{ item.name }}</a-select-option>
        </a-select>
        <div class="field-hint">优惠券仅适用于选中的商品；已删除商品请移除后重新选择。</div>
      </a-form-item>
      <a-form-item label="优惠方式" required>
        <a-radio-group v-model="form.discountType" @change="changeDiscount"><a-radio value="AMOUNT">立减金额</a-radio><a-radio value="RATE">折扣</a-radio></a-radio-group>
        <div>
          <a-input-number v-model="form.discountValue" :min="0.01" :max="form.discountType === 'RATE' ? 0.99 : 99999999.99" :step="form.discountType === 'RATE' ? 0.01 : 1" :precision="2" />
          <span class="unit-label">{{ form.discountType === 'RATE' ? '折扣系数，如 0.9 表示 9 折' : '元' }}</span>
        </div>
      </a-form-item>
      <a-form-item label="使用门槛" required><a-input-number v-model="form.minSpend" :min="0" :max="99999999.99" :precision="2" /><span class="unit-label">元，0 为无门槛</span></a-form-item>
      <a-form-item label="领取后有效期" required><a-input-number v-model="form.validDays" :min="1" :max="36500" :precision="0" /><span class="unit-label">天，不超过活动结束时间</span></a-form-item>
      <a-form-item label="可领取总量"><a-input-number v-model="form.totalCount" :min="Math.max(1, form.claimedCount || 0)" :max="2147483647" :precision="0" placeholder="不限量" /><span class="unit-label">留空不限量，已领取 {{ form.claimedCount || 0 }} 张</span></a-form-item>
      <a-form-item label="活动开始时间"><a-date-picker v-model="startTime" show-time format="YYYY-MM-DD HH:mm:ss" placeholder="不限开始时间" style="width: 100%" /></a-form-item>
      <a-form-item label="活动结束时间"><a-date-picker v-model="endTime" show-time format="YYYY-MM-DD HH:mm:ss" placeholder="不限结束时间" style="width: 100%" /></a-form-item>
      <a-form-item label="状态"><a-radio-group v-model="form.status"><a-radio :value="1">启用</a-radio><a-radio :value="0">停用</a-radio></a-radio-group></a-form-item>
    </a-form>
  </a-modal>
</template>

<script>
import moment from 'moment'

const emptyForm = () => ({ name: '', couponType: 'ALL', discountType: 'AMOUNT', discountValue: 5, minSpend: 0, validDays: 30, totalCount: undefined, commodityIds: [], status: 1, claimedCount: 0 })

export default {
  name: 'CouponEditor',
  props: { visible: Boolean, coupon: { type: Object, default: null } },
  data () { return { form: emptyForm(), saving: false, commodities: [], goodsLoading: false, startTime: null, endTime: null } },
  computed: {
    productOptions () {
      const options = this.commodities.slice()
      this.form.commodityIds.forEach(id => {
        if (!options.some(item => item.id === id)) options.push({ id, name: `商品已删除（ID:${id}）`, deleted: true })
      })
      return options
    }
  },
  watch: {
    visible (value) {
      if (!value) return
      this.form = { ...emptyForm(), ...(this.coupon || {}), commodityIds: this.coupon ? (this.coupon.commodityIds || []).slice() : [] }
      this.startTime = this.form.startTime ? moment(this.form.startTime) : null
      this.endTime = this.form.endTime ? moment(this.form.endTime) : null
      this.goodsLoading = true
      this.$get('/cos/commodity-info/list').then(result => { this.commodities = result.data.data || [] })
        .catch(() => {}).finally(() => { this.goodsLoading = false })
    }
  },
  methods: {
    filterProduct (input, option) { return option.componentOptions.children[0].text.toLowerCase().includes(input.toLowerCase()) },
    changeDiscount () { this.form.discountValue = this.form.discountType === 'RATE' ? 0.9 : 5 },
    close () { if (!this.saving) this.$emit('close') },
    validationError () {
      if (!this.form.name.trim()) return '请输入优惠券名称'
      if (this.form.couponType === 'PRODUCT' && !this.form.commodityIds.length) return '商品专用券至少选择一个商品'
      if (this.form.couponType === 'PRODUCT' && this.form.commodityIds.some(id => !this.commodities.some(item => item.id === id))) return '请移除已删除的商品后重新选择'
      if (!(Number(this.form.discountValue) > 0) || (this.form.discountType === 'RATE' && Number(this.form.discountValue) >= 1)) return '请输入有效的优惠金额或折扣系数'
      if (this.form.minSpend == null || this.form.minSpend === '' || Number(this.form.minSpend) < 0) return '请输入有效的使用门槛'
      if (!Number.isInteger(Number(this.form.validDays)) || Number(this.form.validDays) < 1 || Number(this.form.validDays) > 36500) return '领取后有效期应为1至36500天的整数'
      if (this.form.totalCount != null && this.form.totalCount !== '' && (!Number.isInteger(Number(this.form.totalCount)) || Number(this.form.totalCount) < Math.max(1, this.form.claimedCount))) return '可领取总量必须为正整数，且不低于已领取数量'
      if (this.startTime && this.endTime && this.endTime.isBefore(this.startTime)) return '活动结束时间不能早于开始时间'
      return null
    },
    save () {
      if (this.saving || this.goodsLoading) return
      const error = this.validationError()
      if (error) { this.$message.warning(error); return }
      this.saving = true
      const params = {
        id: this.form.id,
        name: this.form.name.trim(),
        couponType: this.form.couponType,
        commodityIds: this.form.couponType === 'PRODUCT' ? this.form.commodityIds.join(',') : '',
        discountType: this.form.discountType,
        discountValue: this.form.discountValue,
        minSpend: this.form.minSpend,
        validDays: this.form.validDays,
        totalCount: this.form.totalCount == null ? '' : this.form.totalCount,
        startTime: this.startTime ? this.startTime.format('YYYY-MM-DD HH:mm:ss') : '',
        endTime: this.endTime ? this.endTime.format('YYYY-MM-DD HH:mm:ss') : '',
        status: this.form.status
      }
      const request = this.form.id ? this.$put('/cos/coupon/admin', params) : this.$post('/cos/coupon/admin', params)
      request.then(() => { this.$emit('saved') }).catch(() => {}).finally(() => { this.saving = false })
    }
  }
}
</script>

<style scoped>
.unit-label { margin-left: 8px; color: #8a9299; }
.field-hint { color: #8a9299; font-size: 12px; line-height: 20px; }
</style>
