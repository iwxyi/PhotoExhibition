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
    component: () => import('@/views/AlbumDetail.vue')
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
    path: '/admin/folders',
    name: 'AdminFolders',
    component: () => import('@/views/admin/Folders.vue'),
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()
  
  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    next('/admin/login')
  } else if (to.path === '/admin/login' && authStore.isAuthenticated) {
    next('/admin')
  } else {
    next()
  }
})

export default router

