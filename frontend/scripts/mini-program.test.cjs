const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const babel = require('babel-core')
const compiler = require('vue-template-compiler')

const source = fs.readFileSync(path.join(__dirname, '../src/views/system/miniProgram/MiniProgram.vue'), 'utf8')
const component = compiler.parseComponent(source)
const code = babel.transform(component.script.content, {
  babelrc: false,
  presets: [[require.resolve('babel-preset-env'), {targets: {node: 'current'}}], require.resolve('babel-preset-stage-2')]
}).code
const loaded = {exports: {}}
new Function('module', 'exports', code)(loaded, loaded.exports)
const Page = loaded.exports.default

function page () {
  const requests = [], warnings = []
  const vm = Object.assign(Page.data(), {
    $get: async url => ({data: {data: []}}),
    $post: async (url, data) => { requests.push({method: 'POST', url, data}) },
    $put: async (url, data) => { requests.push({method: 'PUT', url, data}) },
    $delete: async url => { requests.push({method: 'DELETE', url}) },
    $message: {success () {}, warning: message => warnings.push(message)}
  })
  Object.entries(Page.methods).forEach(([name, fn]) => { vm[name] = fn.bind(vm) })
  return {vm, requests, warnings}
}

test('banner configuration template compiles with project Vue compiler', () => {
  assert.deepEqual(compiler.compile(component.template.content).errors, [])
})

test('create and edit persist image, sort and status, deletion reloads list', async () => {
  const {vm, requests} = page()
  vm.create()
  Object.assign(vm.form, {title: '活动', imageUrl: 'banner-new.png', sortNum: 8, status: 1})
  await vm.save()
  assert.equal(requests[0].method, 'POST')
  assert.equal(requests[0].data.imageUrl, 'banner-new.png')
  assert.equal(vm.editorVisible, false)
  const row = {id: 5, title: '活动', imageUrl: 'https://example.com/a.png', sortNum: 8, status: 1}
  vm.edit(row)
  Object.assign(vm.form, {sortNum: 0, status: 0})
  assert.equal(row.status, 1, 'editing must not mutate visible list before save')
  await vm.save()
  assert.equal(requests[1].method, 'PUT')
  assert.equal(requests[1].data.status, 0)
  assert.equal(requests[1].data.sortNum, 0)
  await vm.remove(row)
  assert.equal(requests[2].url, '/system/mini-program/banners/5')
  assert.equal(requests[2].method, 'DELETE')
})

test('incomplete images, invalid sort and unfinished upload cannot save', async () => {
  const {vm, requests, warnings} = page()
  vm.create()
  await vm.save()
  vm.form.imageUrl = 'javascript:alert(1)'
  await vm.save()
  vm.form.imageUrl = 'ok.png'; vm.form.sortNum = -1
  await vm.save()
  vm.form.sortNum = 1; vm.uploading = true
  await vm.save(); vm.closeEditor()
  assert.equal(requests.length, 0)
  assert.equal(warnings.length, 3)
  assert.equal(vm.editorVisible, true)
})

test('upload validation rejects invalid file types and files larger than 5MB', () => {
  const {vm} = page()
  assert.equal(vm.beforeUpload({type: 'image/png', size: 12345}), true)
  assert.equal(vm.beforeUpload({type: 'text/html', size: 100}), false)
  assert.equal(vm.beforeUpload({type: 'image/png', size: 5 * 1024 * 1024 + 1}), false)
  assert.equal(vm.imageUrl('banner-example.png'), '/febs/imagesWeb/banner-example.png')
  assert.equal(vm.imageUrl('https://example.com/a.jpg'), 'https://example.com/a.jpg')
})
