/**
 * 修复移动端浏览器在“带速度的手势松手”之后吞掉下一次点按的行为。
 *
 * Chromium 系浏览器在手指以一定速度离开屏幕时会生成 fling 手势。只要 fling
 * 动画还活着（实测约 1~2 秒），浏览器就把下一次点按保留给“停止 fling”：
 * pointerdown / pointerup / touchstart / touchend 照常派发，但不再合成
 * mousedown / mouseup / click。页面无法关闭这个行为，也无法从 CSS 或
 * preventDefault 上规避（touch-action: none、锁定页面滚动、在 touchstart 上
 * preventDefault 都无效）。
 *
 * PhotoViewer 快速下滑关闭正好落在这个窗口里：关闭后相册详情页的第一次点击
 * （返回按钮、缩略图、任何按钮）都没有反应，双击会退化成单击；手指停住再松开
 * 因为没有速度、不产生 fling，所以一切正常。
 *
 * 这里的做法是：手势结束后短暂布防，若检测到“完成了一次点按但 click 没有到达”，
 * 就按松手坐标补发一次 click。判定完全基于“click 是否真的缺失”，不对浏览器版本
 * 或动画是否开启做任何假设：click 正常到达时这段代码是纯旁观，不会补发，也不会
 * 给点击加延迟。
 *
 * 双触发的两种可能都被堵住了：
 * 1. 浏览器比宽限期更晚才派发 click（历史上的 300ms tap delay、主线程繁忙、
 *    某些 WebView）。补发之后短暂拦截“没有新 pointerdown 就到达的可信 click”，
 *    把迟到的那一次吃掉。
 * 2. 用户紧接着的第二次点按。任何新的 pointerdown 都会立刻解除上面的拦截，
 *    所以真实的第二次点击一定能过去，快速双击不会退化成单击。
 */

// fling 抑制窗口实测最长约 2 秒，布防时长覆盖它即可；由于只补偿一次点按，
// 窗口略长也不会影响后续交互。
const REPAIR_WINDOW_MS = 2000
// 等待浏览器自己派发 click 的宽限时间。正常情况下 click 紧跟 pointerup，
// 只有被吞掉时才会等满。
const CLICK_GRACE_MS = 80
// 补发之后拦截迟到的真实 click 的时长。取值需覆盖历史上最长的 tap delay
// （约 350ms）；因为任何新的 pointerdown 都会提前解除，取宽一点是安全的。
const LATE_CLICK_WINDOW_MS = 600
// 超出这个位移/时长的手势不是点按（滑动、长按），不做补偿。
const TAP_MOVE_TOLERANCE_PX = 10
const TAP_DURATION_LIMIT_MS = 500

const makeSyntheticClick = (x: number, y: number) => {
  const init = {
    bubbles: true,
    cancelable: true,
    view: window,
    clientX: x,
    clientY: y,
    button: 0,
    buttons: 0,
    detail: 1
  }
  // click 在现代浏览器里本身就是 PointerEvent，补发时保持一致，
  // 读 e.pointerType 的处理函数才能拿到和真实点按相同的值。
  return typeof PointerEvent === 'function'
    ? new PointerEvent('click', { ...init, pointerType: 'touch', isPrimary: true })
    : new MouseEvent('click', init)
}

export const useFlingTapRepair = () => {
  let armedUntil = 0
  let attached = false
  let sawClick = false
  let pendingTap: { id: number; x: number; y: number; startedAt: number } | null = null
  let suppressLateClickUntil = 0
  let graceTimer: number | null = null
  let expireTimer: number | null = null

  const onClickCapture = (e: MouseEvent) => {
    // 自己补发的那一次（isTrusted 为 false）不参与判定。
    if (!e.isTrusted) return
    if (suppressLateClickUntil && performance.now() <= suppressLateClickUntil) {
      // 已经补发过，这是浏览器迟到的同一次点按，吃掉以免双触发。
      // window 捕获阶段是整条派发链的最前面，这里拦住后没有任何处理函数会看到它。
      e.stopImmediatePropagation()
      e.preventDefault()
      detach()
      return
    }
    sawClick = true
  }

  const onPointerDown = (e: PointerEvent) => {
    // 新的一次点按永远不该被拦截，无条件先解除。
    suppressLateClickUntil = 0
    if (e.pointerType !== 'touch') return
    if (performance.now() > armedUntil) {
      detach()
      return
    }
    sawClick = false
    pendingTap = { id: e.pointerId, x: e.clientX, y: e.clientY, startedAt: performance.now() }
  }

  const onPointerUp = (e: PointerEvent) => {
    if (!pendingTap || e.pointerId !== pendingTap.id) return
    const start = pendingTap
    pendingTap = null
    const moved = Math.hypot(e.clientX - start.x, e.clientY - start.y)
    const elapsed = performance.now() - start.startedAt
    if (performance.now() > armedUntil || moved > TAP_MOVE_TOLERANCE_PX || elapsed > TAP_DURATION_LIMIT_MS) {
      detach()
      return
    }
    // 只补偿紧接着手势的那一次点按，之后立即撤防；监听器留到宽限结束再摘，
    // 否则观察不到浏览器可能仍会派发的 click。
    armedUntil = 0
    const x = e.clientX
    const y = e.clientY
    // 以松手瞬间手指下的元素为目标，而不是宽限结束后再找，避免这段时间里
    // 布局变化（例如 PhotoViewer 卸载）导致补发落到别的元素上。
    const targetAtRelease = document.elementFromPoint(x, y)
    if (graceTimer) clearTimeout(graceTimer)
    graceTimer = window.setTimeout(() => {
      graceTimer = null
      if (sawClick) {
        detach()
        return
      }
      const target = targetAtRelease?.isConnected ? targetAtRelease : document.elementFromPoint(x, y)
      if (!target) {
        detach()
        return
      }
      suppressLateClickUntil = performance.now() + LATE_CLICK_WINDOW_MS
      if (expireTimer) clearTimeout(expireTimer)
      expireTimer = window.setTimeout(detach, LATE_CLICK_WINDOW_MS + 50)
      target.dispatchEvent(makeSyntheticClick(x, y))
    }, CLICK_GRACE_MS)
  }

  const onPointerCancel = (e: PointerEvent) => {
    if (pendingTap && e.pointerId === pendingTap.id) pendingTap = null
  }

  const attach = () => {
    if (attached) return
    attached = true
    window.addEventListener('click', onClickCapture, true)
    window.addEventListener('pointerdown', onPointerDown, true)
    window.addEventListener('pointerup', onPointerUp, true)
    window.addEventListener('pointercancel', onPointerCancel, true)
  }

  const detach = () => {
    if (graceTimer) { clearTimeout(graceTimer); graceTimer = null }
    if (expireTimer) { clearTimeout(expireTimer); expireTimer = null }
    pendingTap = null
    armedUntil = 0
    suppressLateClickUntil = 0
    if (!attached) return
    attached = false
    window.removeEventListener('click', onClickCapture, true)
    window.removeEventListener('pointerdown', onPointerDown, true)
    window.removeEventListener('pointerup', onPointerUp, true)
    window.removeEventListener('pointercancel', onPointerCancel, true)
  }

  /** 在可能产生 fling 的手势结束后调用。 */
  const armFlingTapRepair = () => {
    armedUntil = performance.now() + REPAIR_WINDOW_MS
    attach()
    if (expireTimer) clearTimeout(expireTimer)
    // 窗口内没有发生点按时自动撤防，避免长期挂着全局监听器。
    expireTimer = window.setTimeout(detach, REPAIR_WINDOW_MS + 50)
  }

  return { armFlingTapRepair, disposeFlingTapRepair: detach }
}
