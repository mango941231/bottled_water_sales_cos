const {test} = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const source = fs.readFileSync(path.join(__dirname, '../pages/home/index/index.js'), 'utf8')

function home (get) {
  let page
  vm.runInNewContext(source, {
    getApp: () => ({globalData: {imageBaseUrl: 'http://example.com/febs/imagesWeb/'}}),
    require: () => ({get}),
    Page: definition => { page = definition }
  })
  page.setData = value => Object.assign(page.data, value)
  return page
}

test('homepage loads server banners and resolves uploads without altering external URLs', async () => {
  let request
  const page = home(async (url, data, loading) => {
    request = {url, loading}
    return {data: [{id: 3, imageUrl: 'banner-first.png'}, {id: 9, imageUrl: 'https://images.example.com/banner.jpg'}]}
  })
  assert.equal(page.data.swiperlist.length, 0)
  await page.loadBanners()
  assert.deepEqual(request, {url: 'mini-program/banners', loading: false})
  assert.equal(page.data.swiperlist[0].url, 'http://example.com/febs/imagesWeb/banner-first.png')
  assert.equal(page.data.swiperlist[1].url, 'https://images.example.com/banner.jpg')
})

test('returning to homepage reloads changed banner configuration and empty config stays empty', async () => {
  let calls = 0
  const page = home(async () => ({data: ++calls === 1 ? [{id: 1, imageUrl: 'banner.png'}] : []}))
  page.onShow()
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(page.data.swiperlist.length, 1)
  page.onShow()
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(page.data.swiperlist.length, 0)
  assert.equal(calls, 2)
})

test('late banner requests cannot replace a newer configuration', async () => {
  const pending = []
  const page = home(() => new Promise(resolve => pending.push(resolve)))
  const first = page.loadBanners(), second = page.loadBanners()
  pending[1]({data: [{id: 2, imageUrl: 'new.png'}]}); await second
  pending[0]({data: [{id: 1, imageUrl: 'old.png'}]}); await first
  assert.equal(page.data.swiperlist[0].id, 2)
})
