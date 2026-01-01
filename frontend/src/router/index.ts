import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import Home from '@/views/Home.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/album/:id',
    name: 'Album',
    component: () => import('@/views/AlbumDetail.vue'),
    meta: { transitionName: 'none' } // 禁用页面过渡动画，只使用FLIP动画
  },
  {
    path: '/photo/:id',
    name: 'Photo',
    component: () => import('@/views/PhotoDetail.vue')
  },
  {
    path: '/wall',
    name: 'Wall',
    component: () => import('@/views/PhotoWall.vue')
  },
  {
    path: '/random',
    name: 'Random',
    component: () => import('@/views/RandomGallery.vue')
  },
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('@/views/admin/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/admin/Dashboard.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin/theme',
    name: 'AdminTheme',
    component: () => import('@/views/admin/Theme.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin/albums',
    name: 'AdminAlbums',
    component: () => import('@/views/admin/Albums.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin/photos',
    name: 'AdminPhotos',
    component: () => import('@/views/admin/Photos.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin/tags',
    name: 'AdminTags',
    component: () => import('@/views/admin/Tags.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin/persons',
    name: 'AdminPersons',
    component: () => import('@/views/admin/Persons.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin/faces',
    name: 'AdminFaces',
    component: () => import('@/views/admin/Faces.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin/migration',
    name: 'AdminMigration',
    component: () => import('@/views/admin/Folders.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/admin/file-browser',
    name: 'AdminFileBrowser',
    component: () => import('@/views/admin/FileBrowser.vue'),
    meta: { requiresAuth: true }
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

router.beforeEach((to, from, next) => {
  console.log('[Router] beforeEach:', from.name, '->', to.name, 'at', Date.now())

  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next('/admin/login')
  } else if (to.path === '/admin/login' && authStore.isAuthenticated) {
    next('/admin')
  } else {
    next()
  }
})

router.afterEach((to, from) => {
  console.log('[Router] afterEach:', from.name, '->', to.name, 'at', Date.now())
})

export default router

