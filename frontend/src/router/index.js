import Vue from 'vue'
import Router from 'vue-router'
import MenuView from '@/views/common/MenuView'
import PageView from '@/views/common/PageView'
import LoginView from '@/views/login/Common'
import EmptyPageView from '@/views/common/EmptyPageView'
import HomePageView from '@/views/HomePage'
import db from 'utils/localstorage'
import request from 'utils/request'

Vue.use(Router)

let constRouter = [
  {
    path: '/login',
    name: '登录页',
    component: LoginView
  },
  {
    path: '/index',
    name: '首页',
    redirect: '/home'
  }
]

let router = new Router({
  routes: constRouter
})

const whiteList = ['/login']

let asyncRouter

// 导航守卫，渲染动态路由
router.beforeEach((to, from, next) => {
  if (whiteList.indexOf(to.path) !== -1) {
    next()
    return
  }
  auth(to, next)
})

function auth (to, next) {
  let token = db.get('USER_TOKEN')
  let user = db.get('USER')
  if (typeof token === 'string' && token.length && user && user.username) {
    if (!asyncRouter) {
      // Refresh server-side menu assignments instead of retaining stale browser routes.
      request.get(`menu/${user.username}`).then((res) => {
        asyncRouter = res.data
        save('USER_ROUTER', asyncRouter)
        go(to, next)
      }).catch(() => {
        next('/login')
      })
    } else {
      next()
    }
  } else {
    next('/login')
  }
}

function go (to, next) {
  asyncRouter = filterAsyncRouter(asyncRouter)
  router.addRoutes(asyncRouter)
  next({...to, replace: true})
}

function save (name, data) {
  localStorage.setItem(name, JSON.stringify(data))
}

function filterAsyncRouter (routes) {
  return routes.filter((route) => {
    let component = route.component
    if (component) {
      switch (route.component) {
        case 'MenuView':
          route.component = MenuView
          break
        case 'PageView':
          route.component = PageView
          break
        case 'EmptyPageView':
          route.component = EmptyPageView
          break
        case 'HomePageView':
          route.component = HomePageView
          break
        default:
          route.component = view(component)
      }
      if (route.children && route.children.length) {
        route.children = filterAsyncRouter(route.children)
      }
      return true
    }
  })
}

function view (path) {
  return () => import(`@/views/${path}.vue`).then(mod => mod.default || mod)
}

export default router
