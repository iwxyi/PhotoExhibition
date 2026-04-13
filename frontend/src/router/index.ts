import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { attachExplicitPublicSlug } from '@/utils/publicRoute'
import Home from '@/views/Home.vue'
import AlbumDetail from '@/views/AlbumDetail.vue'
import PhotoDetail from '@/views/PhotoDetail.vue'
import PhotoWall from '@/views/PhotoWall.vue'
import RandomGallery from '@/views/RandomGallery.vue'
import Persons from '@/views/Persons.vue'
import PersonDetail from '@/views/PersonDetail.vue'
import Search from '@/views/Search.vue'

const LAST_ADMIN_ROUTE_KEY = 'pe_last_admin_route'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/:userSlug',
    name: 'HomeWithSlug',
    component: Home
  },
  {
    path: '/album/:id',
    name: 'Album',
    component: AlbumDetail,
    meta: { transitionName: 'none', dynamicTitle: true } // 禁用页面过渡动画，只使用FLIP动画
  },
  {
    path: '/:userSlug/album/:id',
    name: 'AlbumWithSlug',
    component: AlbumDetail,
    meta: { transitionName: 'none', dynamicTitle: true }
  },
  {
    path: '/photo/:id',
    name: 'Photo',
    component: PhotoDetail,
    meta: { dynamicTitle: true }
  },
  {
    path: '/:userSlug/photo/:id',
    name: 'PhotoWithSlug',
    component: PhotoDetail,
    meta: { dynamicTitle: true }
  },
  {
    path: '/wall',
    name: 'Wall',
    component: PhotoWall,
    meta: { title: '图墙' }
  },
  {
    path: '/:userSlug/wall',
    name: 'WallWithSlug',
    component: PhotoWall,
    meta: { title: '图墙' }
  },
  {
    path: '/random',
    name: 'Random',
    component: RandomGallery,
    meta: { title: '随机画廊' }
  },
  {
    path: '/:userSlug/random',
    name: 'RandomWithSlug',
    component: RandomGallery,
    meta: { title: '随机画廊' }
  },
  {
    path: '/persons',
    name: 'Persons',
    component: Persons,
    meta: { title: '人物' }
  },
  {
    path: '/:userSlug/persons',
    name: 'PersonsWithSlug',
    component: Persons,
    meta: { title: '人物' }
  },
  {
    path: '/person/:id',
    name: 'Person',
    component: PersonDetail,
    meta: { transitionName: 'none', dynamicTitle: true } // 禁用页面过渡动画，只使用FLIP动画
  },
  {
    path: '/:userSlug/person/:id',
    name: 'PersonWithSlug',
    component: PersonDetail,
    meta: { transitionName: 'none', dynamicTitle: true }
  },
  // 短路由：/a/关键词 -> 相册（直接渲染，无需重定向）
  {
    path: '/a/:keyword',
    name: 'AlbumShort',
    component: AlbumDetail,
    meta: { transitionName: 'none', dynamicTitle: true }
  },
  {
    path: '/:userSlug/a/:keyword',
    name: 'AlbumShortWithSlug',
    component: AlbumDetail,
    meta: { transitionName: 'none', dynamicTitle: true }
  },
  // 短路由：/p/关键词 -> 人物（直接渲染，无需重定向）
  {
    path: '/p/:keyword',
    name: 'PersonShort',
    component: PersonDetail,
    meta: { transitionName: 'none', dynamicTitle: true }
  },
  {
    path: '/:userSlug/p/:keyword',
    name: 'PersonShortWithSlug',
    component: PersonDetail,
    meta: { transitionName: 'none', dynamicTitle: true }
  },
  // 短路由：/s/关键词 -> 搜索
  {
    path: '/s/:keyword',
    name: 'SearchShort',
    component: Search,
    meta: { dynamicTitle: true }
  },
  {
    path: '/:userSlug/s/:keyword',
    name: 'SearchShortWithSlug',
    component: Search,
    meta: { dynamicTitle: true }
  },
  // 搜索页面（支持查询参数）
  {
    path: '/search',
    name: 'Search',
    component: Search,
    meta: { dynamicTitle: true }
  },
  {
    path: '/:userSlug/search',
    name: 'SearchWithSlug',
    component: Search,
    meta: { dynamicTitle: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresGuest: true, title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { requiresGuest: true, title: '注册' }
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/ForgotPassword.vue'),
    meta: { requiresGuest: true, title: '找回密码' }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
    meta: { requiresAuth: true, title: '个人资料' }
  },
  {
    path: '/vip',
    name: 'VipCenter',
    component: () => import('@/views/VipCenter.vue'),
    meta: { requiresAuth: true, title: '会员中心' }
  },
  {
    path: '/vip/result',
    name: 'PaymentResult',
    component: () => import('@/views/PaymentResult.vue'),
    meta: { title: '支付结果' }
  },
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('@/views/admin/Login.vue'),
    meta: { requiresAuth: false, title: '登录' }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/admin/Dashboard.vue'),
    meta: { requiresAuth: true, title: '后台管理' }
  },
  {
    path: '/admin/theme',
    name: 'AdminTheme',
    component: () => import('@/views/admin/Theme.vue'),
    meta: { requiresAuth: true, title: '主题设置' }
  },
  {
    path: '/admin/albums',
    name: 'AdminAlbums',
    component: () => import('@/views/admin/Albums.vue'),
    meta: { requiresAuth: true, title: '相册管理' }
  },
  {
    path: '/admin/photos',
    name: 'AdminPhotos',
    component: () => import('@/views/admin/Photos.vue'),
    meta: { requiresAuth: true, title: '照片管理' }
  },
  {
    path: '/admin/tags',
    name: 'AdminTags',
    component: () => import('@/views/admin/Tags.vue'),
    meta: { requiresAuth: true, title: '标签管理' }
  },
  {
    path: '/admin/persons',
    name: 'AdminPersons',
    component: () => import('@/views/admin/Persons.vue'),
    meta: { requiresAuth: true, title: '人物管理' }
  },
  {
    path: '/admin/persons/batch-assign',
    name: 'AdminPersonsBatchAssign',
    component: () => import('@/views/admin/FaceBatchAssign.vue'),
    meta: { requiresAuth: true, title: '批量分配' }
  },
  {
    path: '/admin/faces',
    name: 'AdminFaces',
    component: () => import('@/views/admin/Faces.vue'),
    meta: { requiresAuth: true, title: '人脸管理' }
  },
  {
    path: '/admin/file-browser',
    name: 'AdminFileBrowser',
    component: () => import('@/views/admin/FileBrowser.vue'),
    meta: { requiresAuth: true, title: '文件浏览器' }
  },
  {
    path: '/admin/settings',
    name: 'AdminSettings',
    component: () => import('@/views/admin/Settings.vue'),
    meta: { requiresAuth: true, title: '系统设置' }
  },
  {
    path: '/admin/super-admin',
    name: 'AdminSuperAdmin',
    component: () => import('@/views/admin/SuperAdmin.vue'),
    meta: { requiresAuth: true, requiresSuperAdmin: true, title: '超级管理员' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    // 让组件自己管理滚动位置，不在路由层面干预
    return false
  }
})

router.beforeEach(async (to, from, next) => {
  // router navigation debug removed

  const authStore = useAuthStore()
  const hasUserSlug = typeof to.params.userSlug === 'string' && to.params.userSlug.length > 0
  const isPublicContentRoute =
    !to.path.startsWith('/admin') &&
    to.path !== '/login' &&
    to.path !== '/register' &&
    !to.path.startsWith('/profile') &&
    !to.path.startsWith('/vip')

  if (isPublicContentRoute && !authStore.publicSettingsLoaded) {
    await authStore.fetchPublicSettings()
  }

  if (authStore.multiUserEnabled && isPublicContentRoute && !hasUserSlug) {
    if (authStore.isAuthenticated && authStore.slug) {
      next(attachExplicitPublicSlug(to.fullPath || to.path || '/', authStore.slug))
      return
    }
    if (to.path !== '/') {
      next('/')
      return
    }
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    const redirect = encodeURIComponent(to.fullPath)
    next(to.path.startsWith('/admin') ? '/admin/login' : `/login?redirect=${redirect}`)
  } else if (to.meta.requiresSuperAdmin && authStore.role !== 'SUPER_ADMIN') {
    next('/admin')
  } else if ((to.path === '/admin/login' || to.meta.requiresGuest) && authStore.isAuthenticated) {
    next(to.path === '/admin/login' ? '/admin' : '/profile')
  } else {
    next()
  }
})

router.afterEach((to, from) => {
  if (to.path.startsWith('/admin') && to.path !== '/admin/login') {
    localStorage.setItem(LAST_ADMIN_ROUTE_KEY, to.fullPath)
  }
})

export default router
