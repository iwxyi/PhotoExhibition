<template>
  <div class="atmosphere-effects">
    <!-- 下雪特效 -->
    <div
      v-if="hasEffect('snow')"
      class="snow-container"
      :class="getEffectClass('snow')"
    >
      <div
        v-for="flake in getEffectConfig('snow').particleCount"
        :key="flake"
        class="snow-flake"
        :style="getSnowFlakeStyle(flake)"
      ></div>
    </div>

    <!-- 樱花特效 -->
    <div
      v-if="hasEffect('cherry_blossom')"
      class="cherry-blossom-container"
      :class="getEffectClass('cherry_blossom')"
    >
      <div
        v-for="petal in getEffectConfig('cherry_blossom').particleCount"
        :key="petal"
        class="cherry-petal"
        :style="getCherryPetalStyle(petal)"
      ></div>
    </div>

    <!-- 生日特效 -->
    <div
      v-if="hasEffect('birthday')"
      class="birthday-container"
      :class="getEffectClass('birthday')"
    >
      <!-- 气球 -->
      <div
        v-for="balloon in getEffectConfig('birthday').balloonCount"
        :key="'balloon-' + balloon"
        class="birthday-balloon"
        :style="getBirthdayBalloonStyle(balloon)"
      ></div>

      <!-- 彩屑 -->
      <div
        v-for="confetti in getEffectConfig('birthday').confettiCount"
        :key="'confetti-' + confetti"
        class="birthday-confetti"
        :style="getBirthdayConfettiStyle(confetti)"
      ></div>
    </div>

    <!-- 流星特效 -->
    <div
      v-if="hasEffect('meteor')"
      class="meteor-container"
      :class="getEffectClass('meteor')"
    >
      <div
        v-for="meteor in getEffectConfig('meteor').meteorCount"
        :key="'meteor-' + meteor"
        class="meteor"
        :style="getMeteorStyle(meteor)"
      ></div>
    </div>

    <!-- 星空特效 -->
    <div
      v-if="hasEffect('starry_sky')"
      class="starry-sky-container"
      :class="getEffectClass('starry_sky')"
    >
      <div
        v-for="star in getEffectConfig('starry_sky').starCount"
        :key="'star-' + star"
        class="star"
        :style="getStarStyle(star)"
      ></div>
    </div>

    <!-- 烟花特效 -->
    <div
      v-if="hasEffect('fireworks')"
      class="fireworks-container"
      :class="getEffectClass('fireworks')"
    >
      <div
        v-for="firework in getEffectConfig('fireworks').fireworkCount"
        :key="'firework-' + firework"
        class="firework"
        :style="getFireworkStyle(firework)"
      ></div>
    </div>

    <!-- 秋叶特效 -->
    <div
      v-if="hasEffect('autumn_leaves')"
      class="autumn-leaves-container"
      :class="getEffectClass('autumn_leaves')"
    >
      <div
        v-for="leaf in getEffectConfig('autumn_leaves').leafCount"
        :key="'leaf-' + leaf"
        class="autumn-leaf"
        :style="getAutumnLeafStyle(leaf)"
      ></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface AtmosphereEffect {
  type: string
  intensity: string
  config?: Record<string, any>
}

interface Props {
  effects: AtmosphereEffect[]
}

const props = defineProps<Props>()

const hasEffect = (type: string) => {
  return props.effects?.some(effect => effect.type === type) || false
}

const getEffectConfig = (type: string) => {
  const effect = props.effects?.find(e => e.type === type)
  return effect?.config || {}
}

const getEffectClass = (type: string) => {
  const effect = props.effects?.find(e => e.type === type)
  return `intensity-${effect?.intensity || 'medium'}`
}

// 下雪特效样式
const getSnowFlakeStyle = (index: number) => {
  const config = getEffectConfig('snow')
  const size = config.size || 3
  const speed = config.speed || 1
  const delay = (index * 1000) % 10000 // 循环延迟

  return {
    left: `${Math.random() * 100}%`,
    animationDelay: `${delay}ms`,
    animationDuration: `${5000 / speed}ms`,
    width: `${size + Math.random() * 2}px`,
    height: `${size + Math.random() * 2}px`,
    opacity: 0.6 + Math.random() * 0.4
  }
}

// 樱花特效样式
const getCherryPetalStyle = (index: number) => {
  const config = getEffectConfig('cherry_blossom')
  const size = config.size || 4
  const speed = config.speed || 1
  const delay = (index * 2000) % 15000

  return {
    left: `${Math.random() * 100}%`,
    animationDelay: `${delay}ms`,
    animationDuration: `${8000 / speed}ms`,
    width: `${size + Math.random() * 3}px`,
    height: `${size + Math.random() * 3}px`,
    transform: `rotate(${Math.random() * 360}deg)`
  }
}

// 生日气球样式
const getBirthdayBalloonStyle = (index: number) => {
  const colors = ['#ff6b6b', '#4ecdc4', '#45b7d1', '#f9ca24', '#f0932b', '#eb4d4b', '#6c5ce7']
  const color = colors[index % colors.length]
  const delay = (index * 1000) % 8000

  return {
    left: `${10 + (index * 15) % 80}%`,
    backgroundColor: color,
    animationDelay: `${delay}ms`,
    transform: `scale(${0.8 + Math.random() * 0.4})`
  }
}

// 生日彩屑样式
const getBirthdayConfettiStyle = (index: number) => {
  const colors = ['#ff6b6b', '#4ecdc4', '#45b7d1', '#f9ca24', '#f0932b', '#eb4d4b', '#6c5ce7']
  const color = colors[index % colors.length]
  const delay = Math.random() * 3000

  return {
    left: `${Math.random() * 100}%`,
    backgroundColor: color,
    animationDelay: `${delay}ms`,
    transform: `rotate(${Math.random() * 360}deg)`
  }
}

// 流星样式
const getMeteorStyle = (index: number) => {
  const config = getEffectConfig('meteor')
  const trailLength = config.trailLength || 150
  const speed = config.speed || 2
  const delay = (index * 3000) % 10000

  return {
    left: `${Math.random() * 100}%`,
    animationDelay: `${delay}ms`,
    animationDuration: `${2000 / speed}ms`,
    '--trail-length': `${trailLength}px`
  }
}

// 星空样式
const getStarStyle = (index: number) => {
  const config = getEffectConfig('starry_sky')
  const brightness = config.brightness || 0.8
  const twinkleSpeed = config.twinkleSpeed || 1
  const delay = Math.random() * 3000

  return {
    left: `${Math.random() * 100}%`,
    top: `${Math.random() * 70}%`,
    animationDelay: `${delay}ms`,
    animationDuration: `${2000 / twinkleSpeed}ms`,
    opacity: brightness * (0.3 + Math.random() * 0.7)
  }
}

// 烟花样式
const getFireworkStyle = (index: number) => {
  const config = getEffectConfig('fireworks')
  const colors = config.colors || ['#ff6b6b', '#4ecdc4', '#45b7d1', '#f9ca24', '#f0932b']
  const delay = (index * 2000) % 8000

  return {
    left: `${20 + (index * 20) % 60}%`,
    top: `${30 + (index * 15) % 40}%`,
    animationDelay: `${delay}ms`,
    '--burst-color': colors[index % colors.length]
  }
}

// 秋叶样式
const getAutumnLeafStyle = (index: number) => {
  const config = getEffectConfig('autumn_leaves')
  const colors = config.colors || ['#d2691e', '#daa520', '#cd853f', '#deb887']
  const color = colors[index % colors.length]
  const fallSpeed = config.fallSpeed || 1
  const delay = (index * 1500) % 12000

  return {
    left: `${Math.random() * 100}%`,
    backgroundColor: color,
    animationDelay: `${delay}ms`,
    animationDuration: `${6000 / fallSpeed}ms`,
    transform: `rotate(${Math.random() * 360}deg) scale(${0.8 + Math.random() * 0.4})`
  }
}
</script>

<style scoped>
.atmosphere-effects {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
  overflow: hidden;
}

/* 下雪特效 */
.snow-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.snow-flake {
  position: absolute;
  background: white;
  border-radius: 50%;
  animation: snowfall linear infinite;
}

@keyframes snowfall {
  0% {
    transform: translateY(-10px) rotate(0deg);
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  90% {
    opacity: 1;
  }
  100% {
    transform: translateY(100vh) rotate(360deg);
    opacity: 0;
  }
}

/* 樱花特效 */
.cherry-blossom-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.cherry-petal {
  position: absolute;
  background: linear-gradient(45deg, #ffb3ba, #ffdfba);
  border-radius: 50% 0 50% 50%;
  animation: cherry-fall linear infinite;
}

@keyframes cherry-fall {
  0% {
    transform: translateY(-20px) rotate(0deg);
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  90% {
    opacity: 0.8;
  }
  100% {
    transform: translateY(100vh) rotate(720deg);
    opacity: 0;
  }
}

/* 生日特效 */
.birthday-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.birthday-balloon {
  position: absolute;
  bottom: -50px;
  width: 30px;
  height: 40px;
  border-radius: 50% 50% 50% 50% / 60% 60% 40% 40%;
  animation: balloon-rise 8s ease-in-out infinite;
}

@keyframes balloon-rise {
  0% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-80vh) scale(1.1);
  }
  100% {
    transform: translateY(-150vh) scale(0.8);
    opacity: 0;
  }
}

.birthday-confetti {
  position: absolute;
  top: -10px;
  width: 8px;
  height: 8px;
  animation: confetti-fall 3s ease-in-out infinite;
}

@keyframes confetti-fall {
  0% {
    transform: translateY(0) rotate(0deg);
    opacity: 1;
  }
  100% {
    transform: translateY(100vh) rotate(720deg);
    opacity: 0;
  }
}

/* 流星特效 */
.meteor-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.meteor {
  position: absolute;
  top: -5px;
  width: 2px;
  height: var(--trail-length);
  background: linear-gradient(to bottom, rgba(255,255,255,0), rgba(255,255,255,0.8));
  animation: meteor-fall linear infinite;
}

@keyframes meteor-fall {
  0% {
    transform: translateY(-100px) rotate(45deg);
    opacity: 0;
  }
  50% {
    opacity: 1;
  }
  100% {
    transform: translateY(100vh) rotate(45deg);
    opacity: 0;
  }
}

/* 星空特效 */
.starry-sky-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.star {
  position: absolute;
  width: 2px;
  height: 2px;
  background: white;
  border-radius: 50%;
  animation: star-twinkle ease-in-out infinite alternate;
}

@keyframes star-twinkle {
  0% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  100% {
    opacity: 1;
    transform: scale(1.2);
  }
}

/* 烟花特效 */
.fireworks-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.firework {
  position: absolute;
  width: 4px;
  height: 4px;
  background: var(--burst-color);
  border-radius: 50%;
  animation: firework-burst 2s ease-out infinite;
}

@keyframes firework-burst {
  0% {
    transform: scale(0) rotate(0deg);
    opacity: 1;
  }
  50% {
    transform: scale(1) rotate(180deg);
    opacity: 0.8;
  }
  100% {
    transform: scale(0) rotate(360deg);
    opacity: 0;
  }
}

/* 秋叶特效 */
.autumn-leaves-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.autumn-leaf {
  position: absolute;
  top: -20px;
  width: 20px;
  height: 30px;
  clip-path: polygon(50% 0%, 100% 25%, 100% 75%, 50% 100%, 0% 75%, 0% 25%);
  animation: leaf-fall linear infinite;
}

@keyframes leaf-fall {
  0% {
    transform: translateY(-20px) rotate(0deg);
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  90% {
    opacity: 0.8;
  }
  100% {
    transform: translateY(100vh) rotate(720deg);
    opacity: 0;
  }
}

/* 强度调整 */
.intensity-low {
  opacity: 0.6;
}

.intensity-medium {
  opacity: 0.8;
}

.intensity-high {
  opacity: 1;
}
</style>


