<template>
  <a-card :bordered="false" class="card-area">
    <h2>首页轮播图配置</h2>
    <p class="help">管理小程序首页顶部轮播图。排序值越小越靠前，停用后不再展示。建议上传比例为 2:1 的横向图片。</p>
    <div class="toolbar"><a-button type="primary" icon="plus" @click="create">新增轮播图</a-button><a-button icon="reload" :loading="loading" @click="load">刷新</a-button></div>
    <a-table :columns="columns" :data-source="banners" :row-key="row => row.id" :loading="loading" :pagination="{pageSize: 10}" :scroll="{x: 960}">
      <template slot="image" slot-scope="value, row"><img class="banner-thumbnail" :src="imageUrl(value)" :alt="row.title || '轮播图'" @click="previewImage = imageUrl(value)" /></template>
      <template slot="title" slot-scope="value">{{ value || '未命名轮播图' }}</template>
      <template slot="status" slot-scope="value"><a-tag :color="value === 1 ? 'green' : 'default'">{{ value === 1 ? '已启用' : '已停用' }}</a-tag></template>
      <template slot="actions" slot-scope="value, row">
        <a @click="edit(row)">编辑</a><a-divider type="vertical" />
        <a-popconfirm title="确认删除此轮播图？" ok-text="删除" cancel-text="取消" @confirm="remove(row)"><a class="delete-action">删除</a></a-popconfirm>
      </template>
    </a-table>
    <a-modal :visible="editorVisible" :title="form.id ? '编辑轮播图' : '新增轮播图'" :width="640" :confirm-loading="saving" :ok-button-props="{props: {disabled: uploading}}" :mask-closable="false" @ok="save" @cancel="closeEditor">
      <a-form layout="vertical">
        <a-form-item label="轮播图名称"><a-input v-model="form.title" :max-length="100" placeholder="用于后台识别，可不填" :disabled="saving" /></a-form-item>
        <a-form-item label="图片" required>
          <a-upload accept="image/jpeg,image/png,image/gif" :show-upload-list="false" :custom-request="uploadImage" :before-upload="beforeUpload" :disabled="uploading || saving">
            <a-button icon="upload" :loading="uploading" :disabled="saving">{{ uploading ? '正在上传' : '上传图片' }}</a-button>
          </a-upload>
          <span class="upload-tip">JPG、PNG、GIF，不超过 10MB</span>
          <a-input v-model="form.imageUrl" class="image-address" placeholder="也可以填写完整的 HTTP/HTTPS 图片地址" :disabled="uploading || saving" />
          <img v-if="form.imageUrl" class="banner-preview" :src="imageUrl(form.imageUrl)" alt="轮播图预览" />
        </a-form-item>
        <a-row :gutter="24">
          <a-col :span="12"><a-form-item label="排序值"><a-input-number v-model="form.sortNum" :min="0" :max="9999" :precision="0" :disabled="saving" style="width: 100%" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="启用状态"><a-switch :checked="form.status === 1" checked-children="启用" un-checked-children="停用" :disabled="saving" @change="form.status = $event ? 1 : 0" /></a-form-item></a-col>
        </a-row>
      </a-form>
    </a-modal>
    <a-modal title="图片预览" :visible="!!previewImage" :footer="null" @cancel="previewImage = ''"><img :src="previewImage" alt="轮播图预览" style="width: 100%" /></a-modal>
  </a-card>
</template>

<script>
const emptyForm = () => ({id: null, title: '', imageUrl: '', sortNum: 0, status: 1})

export default {
  name: 'MiniProgram',
  data () {
    return {
      banners: [],
      loading: false,
      saving: false,
      uploading: false,
      editorVisible: false,
      form: emptyForm(),
      previewImage: '',
      loadSequence: 0,
      editorVersion: 0,
      columns: [
        {title: '图片预览', dataIndex: 'imageUrl', width: 240, scopedSlots: {customRender: 'image'}},
        {title: '轮播图名称', dataIndex: 'title', width: 200, scopedSlots: {customRender: 'title'}},
        {title: '排序值', dataIndex: 'sortNum', width: 100},
        {title: '状态', dataIndex: 'status', width: 100, scopedSlots: {customRender: 'status'}},
        {title: '更新时间', dataIndex: 'updateTime', width: 180},
        {title: '操作', key: 'actions', width: 140, fixed: 'right', scopedSlots: {customRender: 'actions'}}
      ]
    }
  },
  mounted () { this.load() },
  methods: {
    imageUrl (value) {
      const image = String(value || '').trim()
      return /^https?:\/\//i.test(image) ? image : '/febs/imagesWeb/' + image
    },
    load () {
      const sequence = ++this.loadSequence
      this.loading = true
      return this.$get('/system/mini-program/banners').then(result => {
        if (sequence === this.loadSequence) this.banners = result.data.data || []
      }).catch(() => {}).finally(() => { if (sequence === this.loadSequence) this.loading = false })
    },
    create () { this.editorVersion++; this.form = emptyForm(); this.editorVisible = true },
    edit (row) {
      this.editorVersion++
      this.form = {id: row.id, title: row.title, imageUrl: row.imageUrl, sortNum: row.sortNum, status: row.status}
      this.editorVisible = true
    },
    closeEditor () {
      if (this.saving || this.uploading) return
      this.editorVersion++
      this.editorVisible = false
    },
    beforeUpload (file) {
      if (!['image/jpeg', 'image/png', 'image/gif'].includes(file.type) || !file.size || file.size > 10 * 1024 * 1024) {
        this.$message.warning('请选择不超过10MB的 JPG、PNG、GIF 图片')
        return false
      }
      return true
    },
    uploadImage (options) {
      if (this.uploading || this.saving) { options.onError(new Error('请等待当前操作完成')); return }
      const version = this.editorVersion
      const data = new FormData()
      data.append('image', options.file)
      this.uploading = true
      return this.$upload('/system/mini-program/images', data).then(result => {
        if (version === this.editorVersion) this.form.imageUrl = result.data.data
        options.onSuccess(result.data)
      }).catch(error => options.onError(error)).finally(() => { this.uploading = false })
    },
    save () {
      if (this.saving || this.uploading) return
      const image = String(this.form.imageUrl || '').trim()
      const local = /^[A-Za-z0-9][A-Za-z0-9._-]*\.(jpg|jpeg|png|gif|webp)$/i.test(image)
      if (!image || image.length > 1000 || image.includes('..') || (!local && !/^https?:\/\/[^\s/]+(?:\/[^\s]*)?$/i.test(image))) {
        this.$message.warning('请上传图片或填写有效的 HTTP/HTTPS 图片地址')
        return
      }
      if (!Number.isInteger(this.form.sortNum) || this.form.sortNum < 0 || this.form.sortNum > 9999) {
        this.$message.warning('排序值须为0至9999的整数')
        return
      }
      this.saving = true
      const method = this.form.id ? '$put' : '$post'
      return this[method]('/system/mini-program/banners', {...this.form, imageUrl: image}).then(() => {
        this.editorVisible = false
        this.$message.success('轮播图已保存')
        return this.load()
      }).catch(() => {}).finally(() => { this.saving = false })
    },
    remove (row) {
      return this.$delete(`/system/mini-program/banners/${row.id}`).then(() => {
        this.$message.success('轮播图已删除')
        return this.load()
      }).catch(() => {})
    }
  }
}
</script>

<style scoped>
h2 { font-size: 20px; font-weight: 600; margin-bottom: 10px; }
.help, .upload-tip { color: #8a9299; font-size: 13px; }
.toolbar { display: flex; gap: 12px; margin: 24px 0 18px; }
.banner-thumbnail { width: 200px; height: 100px; object-fit: cover; border-radius: 6px; cursor: pointer; background: #f5f5f5; }
.banner-preview { display: block; width: 100%; max-height: 240px; object-fit: contain; margin-top: 12px; background: #f5f5f5; border-radius: 6px; }
.upload-tip { margin-left: 12px; }
.image-address { margin-top: 12px; }
.delete-action { color: #f5222d; }
</style>
