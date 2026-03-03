import type { EffectParamDef } from './particlePresets'

export type ShaderParams = Record<string, number>
export type ShaderRenderFn = (
  ctx: CanvasRenderingContext2D, w: number, h: number, t: number, p: ShaderParams, interaction?: any
) => void

// ─── Aurora Borealis (极光) ──────────────────────────────────────
const renderAurora: ShaderRenderFn = (ctx, w, h, t, p, interaction) => {
  const intensity = (p.intensity ?? 5) / 10
  const speed = (p.speed ?? 5) / 5
  const bands = Math.round((p.bands ?? 5) / 2) + 1

  const scrollBoost = interaction?.scrollVelocity
    ? Math.min(2, 1 + Math.abs(interaction.scrollVelocity) * 0.002)
    : 1

  for (let b = 0; b < bands; b++) {
    const phase = b * 1.3 + t * 0.3 * speed
    const yBase = h * (0.1 + b * 0.12)
    ctx.beginPath()
    ctx.moveTo(0, yBase)
    for (let x = 0; x <= w; x += 4) {
      const y = yBase
        + Math.sin(x * 0.003 + phase) * 40 * scrollBoost
        + Math.sin(x * 0.007 + phase * 1.3) * 25 * scrollBoost
        + Math.sin(x * 0.001 + t * 0.15 * speed) * 60
      ctx.lineTo(x, y)
    }
    ctx.lineTo(w, h)
    ctx.lineTo(0, h)
    ctx.closePath()

    const hue = (120 + b * 40 + t * 8 * speed) % 360
    const grad = ctx.createLinearGradient(0, yBase - 80, 0, yBase + 150)
    grad.addColorStop(0, `hsla(${hue}, 80%, 60%, 0)`)
    grad.addColorStop(0.3, `hsla(${hue}, 80%, 55%, ${0.12 * intensity})`)
    grad.addColorStop(0.6, `hsla(${hue + 20}, 70%, 50%, ${0.08 * intensity})`)
    grad.addColorStop(1, `hsla(${hue + 40}, 60%, 40%, 0)`)
    ctx.fillStyle = grad
    ctx.fill()
  }
}

// ─── God Rays / Tyndall Effect (丁达尔光线) ─────────────────────
const renderLightRays: ShaderRenderFn = (ctx, w, h, t, p) => {
  const intensity = (p.intensity ?? 5) / 10
  const count = Math.round((p.count ?? 5) * 1.5) + 2
  const speed = (p.speed ?? 5) / 5
  const warmth = (p.warmth ?? 5) / 10

  const cx = w * (0.5 + Math.sin(t * 0.1 * speed) * 0.1)
  const cy = -h * 0.05

  for (let i = 0; i < count; i++) {
    const angle = -0.6 + (i / count) * 1.2 + Math.sin(t * 0.2 * speed + i) * 0.08
    const len = h * 1.4
    const bw = 30 + Math.sin(t * 0.3 * speed + i * 2) * 15

    const ex = cx + Math.sin(angle) * len
    const ey = cy + Math.cos(angle) * len

    const grad = ctx.createLinearGradient(cx, cy, ex, ey)
    const r = Math.round(255 - (1 - warmth) * 40)
    const g = Math.round(240 - warmth * 30)
    const b = Math.round(200 - warmth * 80)
    const a = (0.04 + Math.sin(t * 0.4 * speed + i * 1.5) * 0.02) * intensity
    grad.addColorStop(0, `rgba(${r},${g},${b},${a * 1.5})`)
    grad.addColorStop(0.5, `rgba(${r},${g},${b},${a})`)
    grad.addColorStop(1, `rgba(${r},${g},${b},0)`)

    ctx.save()
    ctx.beginPath()
    ctx.moveTo(cx - bw, cy)
    ctx.lineTo(ex - bw * 2, ey)
    ctx.lineTo(ex + bw * 2, ey)
    ctx.lineTo(cx + bw, cy)
    ctx.closePath()
    ctx.fillStyle = grad
    ctx.fill()
    ctx.restore()
  }
}

// ─── Water Ripple (水波纹) ──────────────────────────────────────
const renderWaterRipple: ShaderRenderFn = (ctx, w, h, t, p) => {
  const intensity = (p.intensity ?? 5) / 10
  const speed = (p.speed ?? 5) / 5
  const count = Math.round((p.count ?? 5) / 2) + 1

  for (let r = 0; r < count; r++) {
    const cx = w * (0.2 + (r * 0.3) % 0.8)
    const cy = h * (0.6 + r * 0.08)
    const phase = t * speed + r * 2.5
    const maxRadius = 150 + r * 30

    for (let ring = 0; ring < 4; ring++) {
      const radius = ((phase * 40 + ring * 40) % maxRadius)
      const alpha = (1 - radius / maxRadius) * 0.15 * intensity
      if (alpha <= 0) continue

      ctx.beginPath()
      ctx.ellipse(cx, cy, radius, radius * 0.35, 0, 0, Math.PI * 2)
      ctx.strokeStyle = `rgba(180, 220, 255, ${alpha})`
      ctx.lineWidth = 1.5
      ctx.stroke()
    }
  }
}

// ─── Fog / Mist (雾气) ─────────────────────────────────────────
const renderFog: ShaderRenderFn = (ctx, w, h, t, p) => {
  const density = (p.density ?? 5) / 10
  const speed = (p.speed ?? 5) / 5
  const layers = Math.round((p.layers ?? 5) / 2) + 2

  for (let i = 0; i < layers; i++) {
    const yBase = h * (0.4 + i * 0.15)
    const xOff = Math.sin(t * 0.15 * speed + i * 1.8) * w * 0.15
    const grad = ctx.createRadialGradient(
      w * 0.5 + xOff, yBase, 0,
      w * 0.5 + xOff, yBase, w * 0.7
    )
    const a = (0.06 + Math.sin(t * 0.2 * speed + i) * 0.02) * density
    grad.addColorStop(0, `rgba(200, 210, 220, ${a * 1.2})`)
    grad.addColorStop(0.5, `rgba(180, 195, 210, ${a})`)
    grad.addColorStop(1, `rgba(160, 180, 200, 0)`)
    ctx.fillStyle = grad
    ctx.fillRect(0, 0, w, h)
  }
}

// ─── Bokeh (光斑虚化) ──────────────────────────────────────────
interface BokehCircle { x: number; y: number; r: number; hue: number; spd: number; phase: number }
let bokehCircles: BokehCircle[] = []
let bokehW = 0
let bokehH = 0

const renderBokeh: ShaderRenderFn = (ctx, w, h, t, p) => {
  const intensity = (p.intensity ?? 5) / 10
  const count = Math.round((p.count ?? 5) * 3) + 5
  const size = (p.size ?? 5) / 5
  const colorful = (p.colorful ?? 5) / 10

  if (bokehCircles.length !== count || bokehW !== w || bokehH !== h) {
    bokehW = w; bokehH = h
    bokehCircles = Array.from({ length: count }, () => ({
      x: Math.random() * w,
      y: Math.random() * h,
      r: (30 + Math.random() * 60) * size,
      hue: Math.random() * 360,
      spd: 0.3 + Math.random() * 0.7,
      phase: Math.random() * Math.PI * 2,
    }))
  }

  for (const c of bokehCircles) {
    const alpha = (0.04 + Math.sin(t * c.spd + c.phase) * 0.025) * intensity
    const grad = ctx.createRadialGradient(c.x, c.y, 0, c.x, c.y, c.r)
    const sat = Math.round(30 + colorful * 50)
    grad.addColorStop(0, `hsla(${c.hue + t * 5}, ${sat}%, 75%, ${alpha * 1.5})`)
    grad.addColorStop(0.6, `hsla(${c.hue + t * 5}, ${sat}%, 65%, ${alpha * 0.5})`)
    grad.addColorStop(1, `hsla(${c.hue + t * 5}, ${sat}%, 55%, 0)`)
    ctx.fillStyle = grad
    ctx.beginPath()
    ctx.arc(c.x, c.y, c.r, 0, Math.PI * 2)
    ctx.fill()
  }
}

// ─── Ocean Wave (海浪) ─────────────────────────────────────────
const renderWave: ShaderRenderFn = (ctx, w, h, t, p) => {
  const intensity = (p.intensity ?? 5) / 10
  const speed = (p.speed ?? 5) / 5
  const height_ratio = (p.height ?? 5) / 10

  const waveH = h * 0.12 * height_ratio
  const baseY = h * (0.82 - height_ratio * 0.1)

  for (let layer = 2; layer >= 0; layer--) {
    const phase = t * (0.6 + layer * 0.2) * speed
    const amp = waveH * (0.6 + layer * 0.2)
    ctx.beginPath()
    ctx.moveTo(0, h)
    for (let x = 0; x <= w; x += 3) {
      const y = baseY + layer * 12
        + Math.sin(x * 0.008 + phase) * amp
        + Math.sin(x * 0.015 + phase * 1.5) * amp * 0.4
        + Math.sin(x * 0.003 + phase * 0.5) * amp * 0.6
      ctx.lineTo(x, y)
    }
    ctx.lineTo(w, h)
    ctx.closePath()

    const a = (0.15 - layer * 0.03) * intensity
    const lightness = 35 + layer * 8
    ctx.fillStyle = `hsla(210, 60%, ${lightness}%, ${a})`
    ctx.fill()
  }
}

// ─── Film Grain (胶片噪点) ─────────────────────────────────────
const renderFilmGrain: ShaderRenderFn = (ctx, w, h, _t, p) => {
  const intensity = (p.intensity ?? 5) / 10
  const grain_size = Math.max(1, Math.round((p.size ?? 5) / 2))

  const iw = Math.ceil(w / grain_size)
  const ih = Math.ceil(h / grain_size)
  const imageData = ctx.createImageData(iw, ih)
  const data = imageData.data

  for (let i = 0; i < data.length; i += 4) {
    const v = Math.random() * 255
    data[i] = v
    data[i + 1] = v
    data[i + 2] = v
    data[i + 3] = Math.random() * 30 * intensity
  }

  ctx.save()
  ctx.imageSmoothingEnabled = false
  const tmpCanvas = document.createElement('canvas')
  tmpCanvas.width = iw
  tmpCanvas.height = ih
  const tmpCtx = tmpCanvas.getContext('2d')!
  tmpCtx.putImageData(imageData, 0, 0)
  ctx.drawImage(tmpCanvas, 0, 0, w, h)
  ctx.restore()
}

// ─── Color Shift (色彩流转) ────────────────────────────────────
const renderColorShift: ShaderRenderFn = (ctx, w, h, t, p) => {
  const intensity = (p.intensity ?? 5) / 10
  const speed = (p.speed ?? 5) / 5

  const hue1 = (t * 15 * speed) % 360
  const hue2 = (hue1 + 120) % 360
  const hue3 = (hue1 + 240) % 360

  const x1 = w * (0.3 + Math.sin(t * 0.2 * speed) * 0.3)
  const y1 = h * (0.3 + Math.cos(t * 0.15 * speed) * 0.3)
  const x2 = w * (0.7 + Math.sin(t * 0.25 * speed + 2) * 0.3)
  const y2 = h * (0.7 + Math.cos(t * 0.18 * speed + 2) * 0.3)

  const a = 0.25 * intensity

  const g1 = ctx.createRadialGradient(x1, y1, 0, x1, y1, w * 0.6)
  g1.addColorStop(0, `hsla(${hue1}, 80%, 55%, ${a})`)
  g1.addColorStop(0.6, `hsla(${hue1}, 75%, 50%, ${a * 0.4})`)
  g1.addColorStop(1, `hsla(${hue1}, 70%, 45%, 0)`)
  ctx.fillStyle = g1
  ctx.fillRect(0, 0, w, h)

  const g2 = ctx.createRadialGradient(x2, y2, 0, x2, y2, w * 0.55)
  g2.addColorStop(0, `hsla(${hue2}, 75%, 50%, ${a * 0.85})`)
  g2.addColorStop(0.6, `hsla(${hue2}, 70%, 45%, ${a * 0.3})`)
  g2.addColorStop(1, `hsla(${hue2}, 65%, 40%, 0)`)
  ctx.fillStyle = g2
  ctx.fillRect(0, 0, w, h)

  const x3 = w * (0.5 + Math.cos(t * 0.18 * speed + 4) * 0.25)
  const y3 = h * (0.5 + Math.sin(t * 0.22 * speed + 4) * 0.25)
  const g3 = ctx.createRadialGradient(x3, y3, 0, x3, y3, w * 0.5)
  g3.addColorStop(0, `hsla(${hue3}, 70%, 50%, ${a * 0.7})`)
  g3.addColorStop(0.6, `hsla(${hue3}, 65%, 45%, ${a * 0.25})`)
  g3.addColorStop(1, `hsla(${hue3}, 60%, 40%, 0)`)
  ctx.fillStyle = g3
  ctx.fillRect(0, 0, w, h)
}

// ─── Vignette Breath (暗角呼吸) ────────────────────────────────
const renderVignette: ShaderRenderFn = (ctx, w, h, t, p) => {
  const intensity = (p.intensity ?? 5) / 10
  const speed = (p.speed ?? 5) / 5

  const pulse = 0.7 + Math.sin(t * 0.5 * speed) * 0.15
  const radius = Math.max(w, h) * 0.6 * pulse

  const grad = ctx.createRadialGradient(w / 2, h / 2, radius * 0.4, w / 2, h / 2, radius)
  grad.addColorStop(0, 'rgba(0,0,0,0)')
  grad.addColorStop(0.6, `rgba(0,0,0,${0.1 * intensity})`)
  grad.addColorStop(1, `rgba(0,0,0,${0.5 * intensity})`)
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, w, h)
}

// ─── Lens Flare (镜头光晕) ─────────────────────────────────────
const renderLensFlare: ShaderRenderFn = (ctx, w, h, t, p) => {
  const intensity = (p.intensity ?? 5) / 10
  const speed = (p.speed ?? 5) / 5
  const size = (p.size ?? 5) / 5

  const cx = w * (0.35 + Math.sin(t * 0.12 * speed) * 0.15)
  const cy = h * (0.2 + Math.sin(t * 0.08 * speed) * 0.08)
  const dx = w / 2 - cx
  const dy = h / 2 - cy

  const mainR = 60 * size
  const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, mainR)
  g.addColorStop(0, `rgba(255,250,230,${0.3 * intensity})`)
  g.addColorStop(0.3, `rgba(255,240,200,${0.12 * intensity})`)
  g.addColorStop(1, 'rgba(255,230,180,0)')
  ctx.fillStyle = g
  ctx.beginPath()
  ctx.arc(cx, cy, mainR, 0, Math.PI * 2)
  ctx.fill()

  const ghosts = [0.3, 0.5, 0.7, 0.85, 1.1]
  const hues = [200, 120, 40, 300, 180]
  for (let i = 0; i < ghosts.length; i++) {
    const frac = ghosts[i]
    const gx = cx + dx * frac * 2
    const gy = cy + dy * frac * 2
    const gr = (15 + i * 8) * size
    const a = (0.04 - i * 0.005) * intensity
    if (a <= 0) continue

    const gg = ctx.createRadialGradient(gx, gy, 0, gx, gy, gr)
    gg.addColorStop(0, `hsla(${hues[i]}, 60%, 70%, ${a})`)
    gg.addColorStop(1, `hsla(${hues[i]}, 60%, 60%, 0)`)
    ctx.fillStyle = gg
    ctx.beginPath()
    ctx.arc(gx, gy, gr, 0, Math.PI * 2)
    ctx.fill()
  }
}

// ─── Glitch (故障风) ───────────────────────────────────────────
const renderGlitch: ShaderRenderFn = (ctx, w, h, t, p) => {
  const intensity = (p.intensity ?? 5) / 10
  const speed = (p.speed ?? 5) / 5

  const trigger = Math.sin(t * 3 * speed) + Math.sin(t * 7.3 * speed)
  if (trigger < 1.2) return

  const sliceCount = 3 + Math.floor(Math.random() * 5)
  for (let i = 0; i < sliceCount; i++) {
    const y = Math.random() * h
    const sliceH = 2 + Math.random() * 20
    const offsetX = (Math.random() - 0.5) * 40 * intensity
    const a = 0.1 + Math.random() * 0.15 * intensity
    const hue = Math.random() * 360

    ctx.fillStyle = `hsla(${hue}, 100%, 50%, ${a})`
    ctx.fillRect(offsetX, y, w, sliceH)
  }

  for (let i = 0; i < 2; i++) {
    const y = Math.random() * h
    const lineH = 1 + Math.random() * 2
    ctx.fillStyle = `rgba(255,255,255,${0.15 * intensity})`
    ctx.fillRect(0, y, w, lineH)
  }
}

// ─── Fireworks (彩色烟花绽放) ──────────────────────────────────
interface Firework { x: number; y: number; born: number; hue: number; particles: { angle: number; speed: number; size: number; hue: number }[] }
let fireworks: Firework[] = []
let fwNextSpawn = 0

const fwClickTimes = new Set<number>()

const spawnFirework = (x: number, y: number, t: number) => {
  const count = 40 + Math.floor(Math.random() * 30)
  const hue = Math.random() * 360
  const pts = Array.from({ length: count }, () => ({
    angle: Math.random() * Math.PI * 2,
    speed: 40 + Math.random() * 100,
    size: 2 + Math.random() * 3,
    hue: hue + (Math.random() - 0.5) * 60,
  }))
  fireworks.push({ x, y, born: t, hue, particles: pts })
}

const renderFireworks: ShaderRenderFn = (ctx, w, h, t, p, interaction) => {
  const intensity = (p.intensity ?? 5) / 10
  const freq = (p.speed ?? 5) / 5

  if (interaction?.consumeClicks) {
    const recent = interaction.consumeClicks(performance.now() - 150)
    for (const click of recent) {
      if (!fwClickTimes.has(click.time)) {
        fwClickTimes.add(click.time)
        spawnFirework(click.x, click.y, t)
        if (fwClickTimes.size > 50) {
          const oldest = fwClickTimes.values().next().value
          if (oldest !== undefined) fwClickTimes.delete(oldest)
        }
      }
    }
  }

  if (t > fwNextSpawn) {
    spawnFirework(w * (0.02 + Math.random() * 0.96), h * (0.03 + Math.random() * 0.6), t)
    fwNextSpawn = t + 0.6 / freq + Math.random() * 1.5 / freq
  }

  fireworks = fireworks.filter(fw => t - fw.born < 3)

  for (const fw of fireworks) {
    const age = t - fw.born
    for (const pt of fw.particles) {
      const dist = pt.speed * age * (1 - age * 0.2)
      if (dist < 0) continue
      const px = fw.x + Math.cos(pt.angle) * dist
      const py = fw.y + Math.sin(pt.angle) * dist + age * age * 30
      const alpha = Math.max(0, (1 - age / 2.5)) * intensity
      const r = pt.size * (1 - age * 0.3)
      if (r <= 0 || alpha <= 0) continue

      ctx.beginPath()
      ctx.arc(px, py, r, 0, Math.PI * 2)
      ctx.fillStyle = `hsla(${pt.hue}, 90%, 60%, ${alpha})`
      ctx.fill()
    }
  }
}

// ─── Birthday Candles (生日蜡烛) ──────────────────────────────
const renderBirthday: ShaderRenderFn = (ctx, w, h, t, p) => {
  const intensity = (p.intensity ?? 5) / 10
  const speed = (p.speed ?? 3) / 10
  const candleCount = Math.round((p.count ?? 5) / 2) + 3

  const baseY = h - 4
  const spacing = Math.min(60, (w * 0.6) / candleCount)
  const startX = (w - spacing * (candleCount - 1)) / 2

  for (let i = 0; i < candleCount; i++) {
    const cx = startX + i * spacing
    const candleH = 60 + (i % 3) * 15
    const burnProgress = Math.min(1, t * speed * 0.02)
    const currentH = candleH * (1 - burnProgress * 0.6)
    const candleTop = baseY - currentH

    const hues = [350, 200, 50, 130, 280, 30, 170]
    const hue = hues[i % hues.length]
    ctx.fillStyle = `hsla(${hue}, 70%, 55%, ${0.8 * intensity})`
    ctx.fillRect(cx - 4, candleTop, 8, currentH)
    ctx.fillStyle = `hsla(${hue}, 60%, 45%, ${0.6 * intensity})`
    ctx.fillRect(cx - 4, candleTop, 2, currentH)

    ctx.fillStyle = `rgba(60,60,60,${0.5 * intensity})`
    ctx.fillRect(cx - 0.5, candleTop - 6, 1, 6)

    if (burnProgress < 0.95) {
      const flickerX = Math.sin(t * 8 + i * 3) * 2.5
      const flickerH = 14 + Math.sin(t * 6 + i * 2) * 5
      const flameCenter = candleTop - 6 - flickerH * 0.55

      const fg = ctx.createRadialGradient(cx + flickerX, flameCenter, 0, cx + flickerX, flameCenter, flickerH * 0.8)
      fg.addColorStop(0, `rgba(255,255,210,${0.95 * intensity})`)
      fg.addColorStop(0.25, `rgba(255,210,60,${0.8 * intensity})`)
      fg.addColorStop(0.6, `rgba(255,120,20,${0.35 * intensity})`)
      fg.addColorStop(1, 'rgba(255,60,10,0)')
      ctx.fillStyle = fg
      ctx.beginPath()
      ctx.ellipse(cx + flickerX, flameCenter, 4.5 + Math.sin(t * 5 + i) * 1.5, flickerH * 0.65, 0, 0, Math.PI * 2)
      ctx.fill()

      const glowR = 30 + Math.sin(t * 4 + i) * 6
      const gg = ctx.createRadialGradient(cx, flameCenter, 0, cx, flameCenter, glowR)
      gg.addColorStop(0, `rgba(255,200,80,${0.1 * intensity})`)
      gg.addColorStop(1, 'rgba(255,150,30,0)')
      ctx.fillStyle = gg
      ctx.beginPath()
      ctx.arc(cx, flameCenter, glowR, 0, Math.PI * 2)
      ctx.fill()
    }
  }
}

// ─── Registry ──────────────────────────────────────────────────
export const shaderRegistry: Record<string, ShaderRenderFn> = {
  aurora: renderAurora,
  light_rays: renderLightRays,
  water_ripple: renderWaterRipple,
  fog: renderFog,
  bokeh: renderBokeh,
  wave: renderWave,
  film_grain: renderFilmGrain,
  color_shift: renderColorShift,
  vignette: renderVignette,
  lens_flare: renderLensFlare,
  glitch: renderGlitch,
  fireworks: renderFireworks,
  birthday: renderBirthday,
}

export const shaderEffectMeta: Record<string, { name: string; description: string; category: string }> = {
  aurora:       { name: '极光',       description: '流动的彩色极光波带',     category: '光照' },
  light_rays:   { name: '丁达尔光线', description: '从天而降的体积光束',     category: '光照' },
  lens_flare:   { name: '镜头光晕',   description: '移动的镜头光晕鬼影',     category: '光照' },
  water_ripple: { name: '水波纹',     description: '扩散的同心椭圆涟漪',     category: '水面' },
  wave:         { name: '海浪',       description: '底部涌动的海浪层次',     category: '水面' },
  fog:          { name: '雾气',       description: '缓慢飘动的半透明雾层',   category: '氛围' },
  bokeh:        { name: '光斑虚化',   description: '大面积柔和虚化光斑',     category: '氛围' },
  vignette:     { name: '暗角呼吸',   description: '缓慢呼吸的暗角效果',     category: '氛围' },
  film_grain:   { name: '胶片噪点',   description: '复古胶片颗粒感',         category: '风格' },
  color_shift:  { name: '色彩流转',   description: '缓慢流动的彩色光晕',     category: '风格' },
  glitch:       { name: '故障风',     description: '随机数字故障闪烁',       category: '风格' },
  fireworks:    { name: '烟花',       description: '夜空中彩色烟花绽放',     category: '氛围' },
  birthday:     { name: '生日蜡烛',   description: '底部蜡烛缓慢燃烧',       category: '氛围' },
}

export const shaderParamDefs: Record<string, EffectParamDef[]> = {
  aurora:       [{ key: 'intensity', label: '强度' }, { key: 'speed', label: '流动速度' }, { key: 'bands', label: '波带数' }],
  light_rays:   [{ key: 'intensity', label: '强度' }, { key: 'count', label: '光束数' }, { key: 'speed', label: '摆动速度' }, { key: 'warmth', label: '色温' }],
  lens_flare:   [{ key: 'intensity', label: '强度' }, { key: 'speed', label: '移动速度' }, { key: 'size', label: '光晕大小' }],
  water_ripple: [{ key: 'intensity', label: '强度' }, { key: 'speed', label: '扩散速度' }, { key: 'count', label: '涟漪组数' }],
  wave:         [{ key: 'intensity', label: '透明度' }, { key: 'speed', label: '涌动速度' }, { key: 'height', label: '浪高' }],
  fog:          [{ key: 'density', label: '浓度' }, { key: 'speed', label: '飘动速度' }, { key: 'layers', label: '雾层数' }],
  bokeh:        [{ key: 'intensity', label: '亮度' }, { key: 'count', label: '光斑数' }, { key: 'size', label: '大小' }, { key: 'colorful', label: '色彩丰富度' }],
  vignette:     [{ key: 'intensity', label: '暗角强度' }, { key: 'speed', label: '呼吸速度' }],
  film_grain:   [{ key: 'intensity', label: '噪点密度' }, { key: 'size', label: '颗粒大小' }],
  color_shift:  [{ key: 'intensity', label: '强度' }, { key: 'speed', label: '流动速度' }],
  glitch:       [{ key: 'intensity', label: '故障强度' }, { key: 'speed', label: '触发频率' }],
  fireworks:    [{ key: 'intensity', label: '亮度' }, { key: 'speed', label: '绽放频率' }],
  birthday:     [{ key: 'intensity', label: '亮度' }, { key: 'count', label: '蜡烛数量' }, { key: 'speed', label: '燃烧速度' }],
}

export const isShaderEffect = (type: string): boolean => type in shaderRegistry
