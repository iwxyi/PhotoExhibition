/**
 * 判断「本文档是不是直接在相册详情页上打开/刷新出来的」。
 *
 * 相册的展开/缩回动画依赖 sessionStorage 里由首页写入的封面坐标
 * （AlbumCard 在点击时写 album-cover-rects-<id>）。如果文档是直接落在详情页上
 * 的——刷新，或者粘贴/分享链接打开——那么这些坐标是上一次会话遗留的，与当前
 * 首页的布局、滚动位置、分页状态都对不上，照着它播动画只会让缩略图飞到错误的
 * 位置。这种情况下应该丢弃它们，走无动画导航。
 *
 * 不要用 performance.getEntriesByType('navigation')[0].type === 'reload' 来做这个
 * 判断：那个条目描述的是**整个文档**，不是 SPA 的路由切换。只要用户刷新过一次
 * （开发时几乎必然），它在该文档余生里都保持 'reload'，于是之后每一次客户端跳转
 * 进详情页都会被误判成刷新，把刚写好的封面坐标删掉——两个方向的动画就都没了。
 *
 * 因此这里在模块加载时求值：此刻 bundle 刚开始执行，还没有发生任何路由跳转，
 * location.pathname 就是文档真正的入口地址。
 */

const ALBUM_DETAIL_PATH = /\/(a|album)\/[^/]+\/?$/

const enteredOnAlbumDetail = typeof window !== 'undefined'
  && ALBUM_DETAIL_PATH.test(window.location.pathname)

let pending = enteredOnAlbumDetail

/**
 * 首次调用时，如果本文档是直接落在详情页上的，返回 true；之后恒为 false。
 * 只消费一次，避免同一文档内后续从首页正常点进来的导航被误清理。
 */
export const consumeDirectAlbumDetailEntry = () => {
  if (!pending) return false
  pending = false
  return true
}
