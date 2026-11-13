<template>
  <a-modal :visible="visible" title="商品水票套餐" width="760px" :footer="null" @cancel="$emit('close')">
    <a-form layout="inline" @submit.prevent="save">
      <a-form-item label="关联商品">
        <a-select v-model="form.commodityId" style="width: 190px" placeholder="选择本店商品">
          <a-select-option v-for="item in commodities" :key="item.id" :value="item.id">{{ item.name }}</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="套餐名称"><a-input v-model="form.name" style="width: 140px" placeholder="如 10 桶水票" /></a-form-item>
      <a-form-item label="桶数"><a-input-number v-model="form.ticketNum" :min="1" :precision="0" /></a-form-item>
      <a-form-item label="售价"><a-input-number v-model="form.price" :min="0" :precision="2" /></a-form-item>
      <a-form-item label="有效天数"><a-input-number v-model="form.validDays" :min="1" :precision="0" /></a-form-item>
      <a-form-item><a-button type="primary" html-type="submit" :loading="saving">{{ form.id ? '保存' : '创建套餐' }}</a-button></a-form-item>
      <a-form-item v-if="form.id"><a-button @click="reset">取消编辑</a-button></a-form-item>
    </a-form>
    <a-table class="ticket-table" :columns="columns" :data-source="packages" :row-key="row => row.id" :pagination="false" size="small">
      <template slot="status" slot-scope="value"><a-tag :color="value ? 'green' : 'default'">{{ value ? '销售中' : '已下架' }}</a-tag></template>
      <template slot="actions" slot-scope="value, row">
        <a @click="edit(row)">编辑</a>
        <a-divider type="vertical" />
        <a-popconfirm title="下架该套餐？已购水票不受影响。" @confirm="remove(row)"><a>下架</a></a-popconfirm>
      </template>
    </a-table>
  </a-modal>
</template>

<script>
const emptyForm = () => ({ commodityId: undefined, name: '', ticketNum: 10, price: 0, validDays: 365, status: 1 })

export default {
  name: 'WaterTicket',
  props: { visible: Boolean, commodity: Object },
  data () {
    return {
      form: emptyForm(),
      packages: [],
      commodities: [],
      saving: false,
      columns: [
        { title: '套餐', dataIndex: 'name' },
        { title: '关联商品', dataIndex: 'commodityName' },
        { title: '桶数', dataIndex: 'ticketNum' },
        { title: '售价', dataIndex: 'price' },
        { title: '有效天数', dataIndex: 'validDays' },
        { title: '状态', dataIndex: 'status', scopedSlots: { customRender: 'status' } },
        { title: '操作', scopedSlots: { customRender: 'actions' } }
      ]
    }
  },
  watch: {
    visible (value) { if (value) this.load() }
  },
  methods: {
    load () {
      this.$get('/cos/commodity-info/page', { shopId: this.$store.state.account.user.userId, current: 1, size: 100 }).then(r => {
        this.commodities = r.data.data.records
        if (this.commodity) this.form.commodityId = this.commodity.id
        return this.$get('/cos/water-ticket/packages')
      }).then(r => {
        const names = {}
        this.commodities.forEach(item => { names[item.id] = item.name })
        this.packages = r.data.data.map(item => ({ ...item, commodityName: names[item.commodityId] || `商品 ${item.commodityId}` }))
      })
    },
    reset () { this.form = emptyForm(); if (this.commodity) this.form.commodityId = this.commodity.id },
    edit (row) { this.form = { ...row } },
    save () {
      if (!this.form.commodityId || !this.form.name || !this.form.ticketNum || !this.form.validDays || this.form.price === null) {
        this.$message.warning('请完整填写套餐信息')
        return
      }
      this.saving = true
      this.$post('/cos/water-ticket/packages', this.form).then(() => {
        this.$message.success('套餐已保存')
        this.reset()
        this.load()
      }).finally(() => { this.saving = false })
    },
    remove (row) {
      this.$delete(`/cos/water-ticket/packages/${row.id}`).then(() => {
        this.$message.success('套餐已下架')
        this.load()
      })
    }
  }
}
</script>

<style scoped>
.ticket-table { margin-top: 20px; }
</style>
