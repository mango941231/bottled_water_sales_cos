const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const babel = require('babel-core')
const compiler = require('vue-template-compiler')

// Load the real Vue component scripts, including their editor dependency.
require.extensions['.vue'] = (module, filename) => {
  const component = compiler.parseComponent(fs.readFileSync(filename, 'utf8'))
  const code = babel.transform(component.script.content, {
    filename,
    babelrc: false,
    presets: [[require.resolve('babel-preset-env'), { targets: { node: 'current' } }], require.resolve('babel-preset-stage-2')]
  }).code
  module._compile(code, filename)
}
const Editor = require('../src/views/manage/coupon/CouponEditor.vue').default
const Page = require('../src/views/manage/coupon/Coupon.vue').default
const tick = () => new Promise(resolve => setImmediate(resolve))

function instance (component, overrides = {}) {
  const vm = Object.assign(component.data(), overrides)
  Object.entries(component.methods).forEach(([name, method]) => { vm[name] = method.bind(vm) })
  return vm
}

function editor (coupon = null) {
  const requests = []
  const events = []
  const warnings = []
  const vm = instance(Editor, {
    coupon,
    $get: () => Promise.resolve({ data: { data: [{ id: 2, name: '纯净水' }, { id: 3, name: '矿泉水' }] } }),
    $post: (url, params) => { requests.push({ method: 'POST', url, params }); return Promise.resolve({}) },
    $put: (url, params) => { requests.push({ method: 'PUT', url, params }); return Promise.resolve({}) },
    $emit: event => events.push(event),
    $message: { warning: message => warnings.push(message) }
  })
  Editor.watch.visible.call(vm, true)
  return { vm, requests, events, warnings }
}

test('new product coupon sends every selected product and required scope', async () => {
  const { vm, requests, events } = editor()
  await tick()
  vm.form.name = '两种水通用'
  vm.form.couponType = 'PRODUCT'
  vm.form.commodityIds = [2, 3]
  vm.save()
  await tick()
  assert.equal(requests.length, 1)
  assert.equal(requests[0].method, 'POST')
  assert.equal(requests[0].params.couponType, 'PRODUCT')
  assert.equal(requests[0].params.commodityIds, '2,3')
  assert.deepEqual(events, ['saved'])
})

test('editing to all products clears product restrictions and nullable fields', async () => {
  const original = { id: 10, name: '商品券', couponType: 'PRODUCT', commodityIds: [2, 3], startTime: '2026-11-01 00:00:00', endTime: '2026-12-01 23:59:59', totalCount: 100, claimedCount: 7 }
  const { vm, requests } = editor(original)
  await tick()
  vm.form.couponType = 'ALL'
  vm.form.totalCount = undefined
  vm.startTime = null
  vm.endTime = null
  vm.form.status = 0
  vm.save()
  await tick()
  assert.equal(requests[0].method, 'PUT')
  assert.equal(requests[0].params.couponType, 'ALL')
  assert.equal(requests[0].params.commodityIds, '')
  assert.equal(requests[0].params.totalCount, '')
  assert.equal(requests[0].params.startTime, '')
  assert.equal(requests[0].params.endTime, '')
  assert.equal(requests[0].params.status, 0)
  assert.equal(original.couponType, 'PRODUCT')
  assert.deepEqual(original.commodityIds, [2, 3])
})

test('invalid product selection and quota do not submit', async () => {
  const { vm, requests, warnings } = editor()
  await tick()
  vm.form.name = '商品券'
  vm.form.couponType = 'PRODUCT'
  vm.save()
  vm.form.commodityIds = [999]
  vm.save()
  vm.form.commodityIds = [2]
  vm.form.claimedCount = 7
  vm.form.totalCount = 6
  vm.save()
  assert.equal(requests.length, 0)
  assert.equal(warnings.length, 3)
})

test('page queries encode names and retain disabled status, deletion reloads previous page', async () => {
  const gets = []
  const deleted = []
  const vm = instance(Page, {
    $get: (url, params) => { gets.push({ url, params }); return Promise.resolve({ data: { data: { records: [], total: 0 } } }) },
    $delete: url => { deleted.push(url); return Promise.resolve({}) },
    $message: { success: () => {} }
  })
  vm.query = { name: '水 & 优惠', couponType: 'PRODUCT', status: 0 }
  vm.search()
  await tick()
  assert.equal(gets[0].url, '/cos/coupon/admin/page')
  assert.equal(gets[0].params.name, encodeURIComponent('水 & 优惠'))
  assert.equal(gets[0].params.status, 0)
  assert.equal(gets[0].params.couponType, 'PRODUCT')
  vm.pagination.current = 2
  vm.coupons = [{ id: 10 }]
  vm.remove({ id: 10 })
  await tick()
  assert.deepEqual(deleted, ['/cos/coupon/admin/10'])
  assert.equal(vm.pagination.current, 1)
  assert.equal(vm.loading, false)
})

test('new page and editor templates compile with the project Vue compiler', () => {
  for (const filename of ['Coupon.vue', 'CouponEditor.vue']) {
    const source = fs.readFileSync(path.join(__dirname, '../src/views/manage/coupon', filename), 'utf8')
    const result = compiler.compile(compiler.parseComponent(source).template.content)
    assert.deepEqual(result.errors, [])
  }
})
