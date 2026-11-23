const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { test } = require('node:test')

const source = fs.readFileSync(path.join(__dirname, '../pages/scar/order/index.js'), 'utf8')
const copy = value => JSON.parse(JSON.stringify(value))
const quote = (selected = 100) => ({
  totalPrice: 50, ticketDiscount: 12.5, subtotal: 37.5, couponDiscount: selected ? 8 : 0,
  payable: selected ? 29.5 : 37.5, selectedUserCouponId: selected, couponName: selected ? '满20减8' : '',
  coupons: [
    { userCouponId: 100, name: '满20减8', couponType: 'ALL', discountType: 'AMOUNT', discountValue: 8, minSpend: 20, available: true, discount: 8 },
    { userCouponId: 101, name: '其他商品专用', couponType: 'PRODUCT', discountType: 'RATE', discountValue: 0.9, minSpend: 0, available: false, reason: '没有适用商品' }
  ]
})

function client(respond = () => ({ data: quote() })) {
  let definition
  const calls = [], toasts = [], timers = []
  const context = {
    require: () => ({
      post: async (url, data) => {
        calls.push({ url, data: copy(data) })
        return respond(url, data)
      }
    }),
    getApp: () => ({ globalData: {} }),
    Page: value => { definition = value },
    wx: { showToast: value => toasts.push(value), showLoading() {}, hideLoading() {}, navigateBack() {}, navigateTo() {} },
    setTimeout: callback => timers.push(callback)
  }
  vm.runInNewContext(source, context)
  const page = Object.assign({}, definition, {
    data: copy(definition.data),
    setData(value, callback) { Object.assign(this.data, value); if (callback) callback() }
  })
  Object.assign(page.data, {
    userInfo: { id: 7 }, addressInfo: { id: 17 }, commodityId: 2, orderType: 2,
    orderList: [{ id: 2, orderId: 10, commodityNum: 4, ticketNum: 1, ticketAvailable: 10, unitPrice: 12.5 }],
    quoteLoading: false, selectedUserCouponId: 100
  })
  return { page, calls, toasts }
}

test('server quote supplies actual payable, scope, deductions and available choices', async () => {
  const { page, calls } = client()
  await page.refreshQuote()
  assert.equal(page.data.cashPrice, '29.50')
  assert.equal(page.data.ticketDiscount, '12.50')
  assert.equal(page.data.couponDiscount, '8.00')
  assert.equal(page.data.availableCouponCount, 1)
  assert.equal(page.data.couponOptions[0].scopeText, '全场通用券')
  assert.match(page.data.couponOptions[1].scopeText, /商品专用券/)
  assert.deepEqual(calls[0].data, { userId: 7, userCouponId: 100, lines: [{ commodityId: 2, commodityNum: 4, ticketNum: 1 }] })
})

test('unavailable product coupon cannot be selected and user can choose no coupon', async () => {
  const { page, calls } = client((url, data) => ({ data: quote(data.userCouponId) }))
  await page.refreshQuote()
  page.chooseCoupon({ currentTarget: { dataset: { id: 101 } } })
  assert.equal(calls.length, 1)
  assert.equal(page.data.selectedUserCouponId, 100)
  await page.chooseCoupon({ currentTarget: { dataset: {} } })
  assert.equal(page.data.selectedUserCouponId, null)
  assert.equal(page.data.cashPrice, '37.50')
  assert.equal(calls[1].data.userCouponId, null)
})

for (const [type, endpoint] of [[2, 'buyGoods'], [1, 'completeCart']]) {
  test(`${endpoint} sends owned coupon ID and quantities without trusting displayed prices`, async () => {
    const { page, calls } = client(url => ({ data: url === 'coupon/checkout' ? quote() : true }))
    page.data.orderType = type
    page.data.cashPrice = '0.01'
    await page.submit()
    const request = calls.find(item => item.url === endpoint)
    assert.equal(request.data.userCouponId, 100)
    assert.equal(request.data.orderPrice, undefined)
    assert.equal(request.data.addressId, 17)
    const line = type === 1 ? request.data.lines[0] : request.data
    assert.equal(line.commodityNum, 4)
    assert.equal(line.ticketNum, 1)
    assert.equal(page.data.submitting, true)
    await page.submit()
    assert.equal(calls.filter(item => item.url === endpoint).length, 1)
  })
}

test('quantity or ticket changes refresh coupon eligibility and clear invalid selection', async () => {
  const { page, calls, toasts } = client(() => ({ data: { ...quote(null), selectedCouponError: '适用商品金额未达到使用门槛' } }))
  page.adjustTicket({ currentTarget: { dataset: { index: 0, delta: 1 } } })
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(calls[0].data.lines[0].ticketNum, 2)
  assert.equal(page.data.selectedUserCouponId, null)
  assert.match(toasts[0].title, /门槛/)
  assert.equal(page.data.quoteLoading, false)
})

test('coupon invalidated just before submit blocks that submit instead of charging full price', async () => {
  const { page, calls } = client(() => ({ data: { ...quote(null), selectedCouponError: '优惠券已过期' } }))
  await page.submit()
  assert.deepEqual(calls.map(item => item.url), ['coupon/checkout'])
  assert.equal(page.data.submitting, false)
  assert.equal(page.data.selectedUserCouponId, null)
})

test('failed price validation prevents orders and leaves a retryable state', async () => {
  const { page, calls } = client(() => { throw new Error('优惠计算失败') })
  await page.submit()
  assert.equal(calls.length, 1)
  assert.equal(page.data.quoteError, true)
  assert.equal(page.data.quoteLoading, false)
  assert.equal(page.data.submitting, false)
})

test('late responses cannot overwrite a newer quantity quote', async () => {
  const pending = []
  const { page } = client(() => new Promise(resolve => pending.push(resolve)))
  const first = page.refreshQuote()
  const second = page.refreshQuote()
  pending[1]({ data: { ...quote(), payable: 88 } })
  await second
  pending[0]({ data: { ...quote(), payable: 11 } })
  await first
  assert.equal(page.data.cashPrice, '88.00')
})

test('double taps while quote or order submission is pending create only one order', async () => {
  let finishQuote
  const { page, calls } = client(url => url === 'coupon/checkout'
    ? new Promise(resolve => { finishQuote = resolve }) : { data: true })
  const first = page.submit()
  await page.submit()
  assert.equal(calls.length, 1)
  finishQuote({ data: quote() })
  await first
  assert.equal(calls.filter(item => item.url === 'buyGoods').length, 1)
})
