import { computed, ref, watch, type Ref } from 'vue'

export type NavigationDirection = 'previous' | 'next'

type NavigationOptions = {
  length: number | Ref<number>
  index?: number
}

export function usePhotoViewerNavigation(options: NavigationOptions) {
  const length = typeof options.length === 'number' ? ref(options.length) : options.length
  const currentIndex = ref(Math.max(0, Math.min(length.value - 1, options.index || 0)))
  const outgoingIndex = ref<number | null>(null)
  const incomingIndex = ref<number | null>(null)
  const transitionProgress = ref(0)
  const transitionVelocity = ref(0)
  const transitioning = ref(false)
  const queue = ref<NavigationDirection[]>([])

  const canMove = (direction: NavigationDirection, from = currentIndex.value) =>
    direction === 'previous' ? from > 0 : from < length.value - 1

  const targetIndex = (direction: NavigationDirection, from = currentIndex.value) =>
    direction === 'previous' ? from - 1 : from + 1

  const enqueue = (direction: NavigationDirection) => {
    if (!canMove(direction, currentIndex.value)) return false
    queue.value.push(direction)
    return true
  }

  const begin = (direction: NavigationDirection) => {
    if (transitioning.value) return enqueue(direction)
    if (!canMove(direction)) return false
    outgoingIndex.value = currentIndex.value
    incomingIndex.value = targetIndex(direction)
    transitionProgress.value = 0
    transitionVelocity.value = 0
    transitioning.value = true
    return true
  }

  const update = (progress: number, velocity = 0) => {
    if (!transitioning.value) return
    transitionProgress.value = Math.max(0, Math.min(1, progress))
    transitionVelocity.value = velocity
  }

  const commit = () => {
    if (!transitioning.value || incomingIndex.value === null) return null
    currentIndex.value = incomingIndex.value
    outgoingIndex.value = null
    incomingIndex.value = null
    transitionProgress.value = 0
    transitionVelocity.value = 0
    transitioning.value = false
    return currentIndex.value
  }

  const cancel = () => {
    outgoingIndex.value = null
    incomingIndex.value = null
    transitionProgress.value = 0
    transitionVelocity.value = 0
    transitioning.value = false
  }

  const consumeNext = () => {
    if (transitioning.value) return null
    while (queue.value.length) {
      const direction = queue.value.shift()!
      if (begin(direction)) return direction
    }
    return null
  }

  const clearQueue = () => { queue.value = [] }

  const reset = (index = 0) => {
    currentIndex.value = Math.max(0, Math.min(length.value - 1, index))
    outgoingIndex.value = null
    incomingIndex.value = null
    transitionProgress.value = 0
    transitionVelocity.value = 0
    transitioning.value = false
    queue.value = []
  }

  watch(length, () => {
    if (length.value <= 0) return
    currentIndex.value = Math.min(currentIndex.value, length.value - 1)
    queue.value = queue.value.filter((direction) => canMove(direction, currentIndex.value))
  })

  const state = computed(() => ({
    currentIndex: currentIndex.value,
    outgoingIndex: outgoingIndex.value,
    incomingIndex: incomingIndex.value,
    transitionProgress: transitionProgress.value,
    transitionVelocity: transitionVelocity.value,
    transitioning: transitioning.value,
    queued: queue.value.length
  }))

  return {
    currentIndex,
    outgoingIndex,
    incomingIndex,
    transitionProgress,
    transitionVelocity,
    transitioning,
    queue,
    state,
    canMove,
    begin,
    update,
    commit,
    cancel,
    enqueue,
    consumeNext,
    clearQueue,
    reset
  }
}
