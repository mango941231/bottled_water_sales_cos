const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { test } = require('node:test')

const appRoot = path.resolve(__dirname, '..')
const config = require('../config')
const requestSource = fs.readFileSync(path.join(appRoot, 'utils/request.js'), 'utf8')
  .replace(/export function /g, 'function ')
const detailSource = fs.readFileSync(path.join(appRoot, 'pages/shop/goods/details.js'), 'utf8')

function createClient(payload, statusCode = 200) {
  let definition
  const context = {
    require: id => {
      if (id === '../config') return config
      if (id === '../../../utils/request') return vm.runInContext('({ get, post })', context)
      throw new Error('Unexpected module: ' + id)
    },
    wx: {
      getStorageSync: () => '', showLoading() {}, hideLoading() {}, showToast() {},
      request: options => options.success({ statusCode, data: payload })
    },
    getApp: () => ({ globalData: { StatusBar: 24, imageBaseUrl: config.IMAGE_BASE_URL } }),
    Page: value => { definition = value }
  }
  vm.createContext(context)
  vm.runInContext(requestSource, context)
  vm.runInContext(detailSource, context)
  const page = Object.assign({}, definition, {
    data: Object.assign({}, definition.data),
    setData(value) { Object.assign(this.data, value) }
  })
  return { page, get: (url, params) => vm.runInContext('get', context)(url, params, false) }
}

const goods = {
  id: 3, code: 'COMM-1794796570127', name: '测试桶装水',
  price: 25, orderNum: 2, stockNum: 123, onPut: '1',
  model: '20kg', content: '商品详情', images: 'SA1794800099078.png'
}

for (const [name, payload] of [
  ['legacy response with product code at root', { msg: 'success', ...goods }],
  ['response with product under data', { code: 0, msg: 'success', data: goods }]
]) {
  test(name + ' populates detail price, sales, description and images', async () => {
    const { page } = createClient(payload)
    await page.getGoodsDetail(3)
    assert.ok(page.data.goods, 'the request must reach the detail page')
    for (const key of ['id', 'name', 'price', 'orderNum', 'stockNum', 'model', 'content']) {
      assert.equal(page.data.goods[key], goods[key])
    }
    assert.equal(page.data.swiperlist[0], config.IMAGE_BASE_URL + goods.images)
  })
}

test('business errors, missing products and HTTP errors remain rejected', async () => {
  for (const [payload, statusCode] of [
    [{ code: 500, msg: '加载失败' }, 200],
    [{ code: 404, msg: '商品不存在或已删除' }, 200],
    [{ msg: 'success', ...goods }, 500],
    [{ msg: 'failed', ...goods }, 200]
  ]) {
    await assert.rejects(createClient(payload, statusCode).get('goodsDetail', { commodityId: 3 }))
  }
})

test('legacy compatibility is limited to the goodsDetail endpoint', async () => {
  await assert.rejects(createClient({ msg: 'success', ...goods }).get('home'))
})
