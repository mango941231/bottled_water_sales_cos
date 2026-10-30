import db from 'utils/localstorage'
import request from 'utils/request'
import store from '@/store'

// ============================================================================
// 临时调试开关：跳过登录页，打开主页直接进入主页面
//
// 恢复登录：把下面的 SKIP_LOGIN 改成 false 即可（或者直接删除本文件，
// 同时删除 src/router/index.js 里对 devAuth 的引用）。
//
// 工作方式：
//   1. 后端可用时，用 admin/123456 静默登录，走完整的后端权限与菜单；
//   2. 后端不可用时（端口 9527 没起来），退化为本地模拟登录态 + 模拟菜单，
//      保证前端能直接渲染出主页面。
// ============================================================================
export const SKIP_LOGIN = true

// 静默登录使用的账号
const SILENT_ACCOUNT = { username: 'admin', password: '123456' }

// 模拟登录态（后端不可用时使用）
const MOCK_TOKEN = 'dev-mock-token'
// 远期过期时间，格式与 request.js 中的数值比较保持一致
const MOCK_EXPIRE_TIME = '20991231235959'
const MOCK_USER = {
  id: 'dev-user-id',
  userId: 1,
  username: 'admin',
  roleId: '74',
  roleName: '平台管理员',
  avatar: 'default.jpg',
  ssex: '0',
  mobile: '',
  email: '',
  deptName: '',
  description: '',
  lastLoginTime: ''
}

// 模拟菜单，结构与后端 /menu/{username} 返回的保持一致。
// component 为 'MenuView' / 'EmptyPageView' / 'HomePageView' 时走特殊处理，
// 其它值按 @/views/{component}.vue 懒加载。
export const MOCK_ROUTERS = [
  {
    id: '1',
    parentId: '0',
    path: '/',
    component: 'MenuView',
    name: '根目录',
    icon: 'none',
    redirect: '/home',
    meta: { isShow: true },
    children: [
      {
        id: '2',
        parentId: '1',
        path: '/home',
        component: 'HomePageView',
        name: '首页',
        icon: 'home',
        meta: { isShow: true }
      },
      {
        id: '3',
        parentId: '1',
        path: '/manage',
        component: 'EmptyPageView',
        name: '配送管理',
        icon: 'shopping-cart',
        meta: { isShow: true },
        children: [
          { id: '31', parentId: '3', path: '/manage/order', component: 'manage/order/Order', name: '订单管理', icon: 'form', meta: { isShow: true } },
          { id: '32', parentId: '3', path: '/manage/commodity', component: 'manage/commodity/Commodity', name: '商品管理', icon: 'shop', meta: { isShow: true } },
          { id: '33', parentId: '3', path: '/manage/commodityType', component: 'manage/commodityType/CommodityType', name: '商品类型', icon: 'tags', meta: { isShow: true } },
          { id: '34', parentId: '3', path: '/manage/shop', component: 'manage/shop/Shop', name: '店铺管理', icon: 'shop', meta: { isShow: true } },
          { id: '35', parentId: '3', path: '/manage/address', component: 'manage/address/Address', name: '地址管理', icon: 'environment', meta: { isShow: true } },
          { id: '36', parentId: '3', path: '/manage/post', component: 'manage/post/Post', name: '帖子管理', icon: 'file-text', meta: { isShow: true } }
        ]
      },
      {
        id: '4',
        parentId: '1',
        path: '/user',
        component: 'EmptyPageView',
        name: '用户中心',
        icon: 'team',
        meta: { isShow: true },
        children: [
          { id: '41', parentId: '4', path: '/manage/user', component: 'manage/user/User', name: '用户管理', icon: 'user', meta: { isShow: true } },
          { id: '42', parentId: '4', path: '/manage/audit', component: 'manage/audit/Audit', name: '审核管理', icon: 'audit', meta: { isShow: true } },
          { id: '43', parentId: '4', path: '/manage/message', component: 'manage/message/Message', name: '消息管理', icon: 'message', meta: { isShow: true } },
          { id: '44', parentId: '4', path: '/manage/evaluation', component: 'manage/evaluation/Evaluation', name: '评价管理', icon: 'star', meta: { isShow: true } },
          { id: '45', parentId: '4', path: '/manage/bulletin', component: 'manage/bulletin/Bulletin', name: '公告管理', icon: 'notification', meta: { isShow: true } }
        ]
      },
      {
        id: '5',
        parentId: '1',
        path: '/shop',
        component: 'EmptyPageView',
        name: '店铺端',
        icon: 'shop',
        meta: { isShow: true },
        children: [
          { id: '51', parentId: '5', path: '/shop/order', component: 'shop/order/Order', name: '店铺订单', icon: 'form', meta: { isShow: true } },
          { id: '52', parentId: '5', path: '/shop/commodity', component: 'shop/commodity/Commodity', name: '店铺商品', icon: 'shop', meta: { isShow: true } },
          { id: '53', parentId: '5', path: '/shop/evaluation', component: 'shop/evaluation/Evaluation', name: '店铺评价', icon: 'star', meta: { isShow: true } },
          { id: '54', parentId: '5', path: '/shop/message', component: 'shop/message/Message', name: '店铺消息', icon: 'message', meta: { isShow: true } },
          { id: '55', parentId: '5', path: '/shop/chat', component: 'shop/chat/Chat', name: '店铺聊天', icon: 'message', meta: { isShow: true } },
          { id: '56', parentId: '5', path: '/shop/personal', component: 'shop/personal/Personal', name: '店铺认证', icon: 'solution', meta: { isShow: true } }
        ]
      },
      {
        id: '6',
        parentId: '1',
        path: '/system',
        component: 'EmptyPageView',
        name: '系统管理',
        icon: 'setting',
        meta: { isShow: true },
        children: [
          { id: '61', parentId: '6', path: '/system/user', component: 'system/user/User', name: '用户管理', icon: 'user', meta: { isShow: true } },
          { id: '62', parentId: '6', path: '/system/role', component: 'system/role/Role', name: '角色管理', icon: 'idcard', meta: { isShow: true } },
          { id: '63', parentId: '6', path: '/system/menu', component: 'system/menu/Menu', name: '菜单管理', icon: 'menu-fold', meta: { isShow: true } },
          { id: '64', parentId: '6', path: '/system/dept', component: 'system/dept/Dept', name: '部门管理', icon: 'cluster', meta: { isShow: true } },
          { id: '65', parentId: '6', path: '/system/dict', component: 'system/dict/Dict', name: '字典管理', icon: 'profile', meta: { isShow: true } }
        ]
      },
      {
        id: '7',
        parentId: '1',
        path: '/monitor',
        component: 'EmptyPageView',
        name: '系统监控',
        icon: 'dashboard',
        meta: { isShow: true },
        children: [
          { id: '71', parentId: '7', path: '/monitor/online', component: 'monitor/Online', name: '在线用户', icon: 'wifi', meta: { isShow: true } },
          { id: '72', parentId: '7', path: '/monitor/systemLog', component: 'monitor/SystemLog', name: '系统日志', icon: 'file-text', meta: { isShow: true } },
          { id: '73', parentId: '7', path: '/monitor/httptrace', component: 'monitor/Httptrace', name: '链路追踪', icon: 'api', meta: { isShow: true } },
          { id: '74', parentId: '7', path: '/monitor/jvmInfo', component: 'monitor/JvmInfo', name: 'JVM信息', icon: 'code', meta: { isShow: true } },
          { id: '75', parentId: '7', path: '/monitor/redisInfo', component: 'monitor/RedisInfo', name: 'Redis信息', icon: 'database', meta: { isShow: true } },
          { id: '76', parentId: '7', path: '/monitor/redisTerminal', component: 'monitor/RedisTerminal', name: 'Redis终端', icon: 'console', meta: { isShow: true } },
          { id: '77', parentId: '7', path: '/monitor/systemInfo', component: 'monitor/SystemInfo', name: '系统信息', icon: 'desktop', meta: { isShow: true } },
          { id: '78', parentId: '7', path: '/monitor/tomcatInfo', component: 'monitor/TomcatInfo', name: 'Tomcat信息', icon: 'cloud-server', meta: { isShow: true } }
        ]
      },
      {
        id: '8',
        parentId: '1',
        path: '/quartz',
        component: 'EmptyPageView',
        name: '定时任务',
        icon: 'clock-circle',
        meta: { isShow: true },
        children: [
          { id: '81', parentId: '8', path: '/quartz/job', component: 'quartz/job/Job', name: '定时任务', icon: 'schedule', meta: { isShow: true } },
          { id: '82', parentId: '8', path: '/quartz/log', component: 'quartz/log/JobLog', name: '调度日志', icon: 'profile', meta: { isShow: true } }
        ]
      },
      {
        id: '9',
        parentId: '1',
        path: '/personal/profile',
        component: 'personal/Profile',
        name: '个人中心',
        icon: 'user',
        meta: { isShow: true }
      }
    ]
  }
]

// 模拟权限 / 角色：让带 v-hasPermission 的按钮在模拟模式下也能显示
function allowAll () {
  const list = []
  list.includes = () => true
  return list
}

// 模拟登录态标记：存在说明当前是本地模拟会话，
// 下次进入时优先再尝试一次真实登录，后端起来后会自动切回真实身份
const MOCK_FLAG = 'DEV_MOCK_SESSION'

let booting = null

/**
 * 静默登录：后端可用时走真实登录，后端不可用时退化为模拟登录态。
 * 幂等，多次调用只会真正执行一次。
 */
export function ensureLogin () {
  if (booting) {
    return booting
  }
  booting = (async () => {
    if (hasSession()) {
      return true
    }
    try {
      const res = await request.post('login', SILENT_ACCOUNT)
      applyLoginData(res.data.data)
      db.remove(MOCK_FLAG)
      // 之前如果用过模拟菜单，这里清掉，让路由守卫重新拉取真实菜单
      const cached = db.get('USER_ROUTER', null)
      if (cached && cached.__mock) {
        db.remove('USER_ROUTER')
      }
      return true
    } catch (e) {
      console.warn('[devAuth] 静默登录失败，已切换为本地模拟登录态：', e && e.message)
      applyMockLogin()
      return true
    }
  })()
  return booting
}

function hasSession () {
  // 模拟会话不视为有效会话，每次进入都再尝试一次真实登录，
  // 这样后端起来之后会自动切换回真实账号
  if (db.get(MOCK_FLAG, false)) {
    return false
  }
  return !!db.get('USER_TOKEN', '') && !!db.get('USER', null)
}

function applyLoginData (data) {
  if (!data) {
    return
  }
  store.commit('account/setToken', data.token)
  store.commit('account/setExpireTime', data.exipreTime)
  store.commit('account/setUser', data.user)
  store.commit('account/setPermissions', data.permissions || [])
  store.commit('account/setRoles', data.roles || [])
  const config = data.config
  if (config) {
    store.commit('setting/setTheme', config.theme)
    store.commit('setting/setLayout', config.layout)
    store.commit('setting/setMultipage', config.multiPage === '1')
    store.commit('setting/fixSiderbar', config.fixSiderbar === '1')
    store.commit('setting/fixHeader', config.fixHeader === '1')
    store.commit('setting/setColor', config.color)
  }
}

function applyMockLogin () {
  store.commit('account/setToken', MOCK_TOKEN)
  store.commit('account/setExpireTime', MOCK_EXPIRE_TIME)
  store.commit('account/setUser', MOCK_USER)
  store.commit('account/setPermissions', allowAll())
  store.commit('account/setRoles', allowAll())
  const routers = JSON.parse(JSON.stringify(MOCK_ROUTERS))
  routers[0].__mock = true
  db.save('USER_ROUTER', routers)
  db.save(MOCK_FLAG, true)
}
