const RESERVED_ROOTS = new Set([
  'admin',
  'login',
  'register',
  'profile',
  'vip',
  'wall',
  'random',
  'persons',
  'person',
  'album',
  'photo',
  'search',
  'a',
  'p',
  's'
])

const PUBLIC_CHILD_SEGMENTS = new Set([
  'album',
  'photo',
  'wall',
  'random',
  'persons',
  'person',
  'search',
  'a',
  'p',
  's'
])

export const getCurrentPublicSlug = (pathname?: string): string | null => {
  const path = pathname ?? (typeof window !== 'undefined' ? window.location.pathname : '')
  const segments = path.split('/').filter(Boolean)
  if (!segments.length) return null

  const [first, second] = segments
  if (RESERVED_ROOTS.has(first)) {
    return null
  }
  if (!second || PUBLIC_CHILD_SEGMENTS.has(second)) {
    return first
  }
  return null
}

export const stripPublicSlug = (pathname?: string): string => {
  const path = pathname ?? (typeof window !== 'undefined' ? window.location.pathname : '')
  const slug = getCurrentPublicSlug(path)
  if (!slug) {
    return path || '/'
  }
  if (path === `/${slug}`) {
    return '/'
  }
  if (path.startsWith(`/${slug}/`)) {
    return path.slice(slug.length + 1) || '/'
  }
  return path || '/'
}

export const buildPublicPath = (path: string, pathname?: string): string => {
  if (!path.startsWith('/')) {
    return path
  }
  const slug = getCurrentPublicSlug(pathname)
  if (!slug) {
    return path
  }
  if (path === '/') {
    return `/${slug}`
  }
  if (path.startsWith('/admin') || path.startsWith('/login') || path.startsWith('/register') || path.startsWith('/profile') || path.startsWith('/vip')) {
    return path
  }
  if (path === `/${slug}` || path.startsWith(`/${slug}/`)) {
    return path
  }
  return `/${slug}${path}`
}

export const attachExplicitPublicSlug = (path: string, slug?: string | null): string => {
  if (!path.startsWith('/') || !slug) {
    return path
  }
  if (path === `/${slug}` || path.startsWith(`/${slug}/`)) {
    return path
  }
  if (path.startsWith('/admin') || path.startsWith('/login') || path.startsWith('/register') || path.startsWith('/profile') || path.startsWith('/vip')) {
    return path
  }
  return path === '/' ? `/${slug}` : `/${slug}${path}`
}

export const getDefaultPostAuthPath = (slug?: string | null, multiUserEnabled?: boolean): string => {
  if (!multiUserEnabled || !slug) {
    return '/'
  }
  return `/${slug}`
}

export const shouldAttachUserSlug = (url?: string): boolean => {
  if (!url) return false
  return url.startsWith('/albums') || url.startsWith('/photos') || url.startsWith('/public') || url.startsWith('/comments')
}
