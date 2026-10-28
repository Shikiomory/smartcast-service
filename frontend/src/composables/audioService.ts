import {ref} from 'vue'
import {Howl} from 'howler'
import {request} from './api'
import type {MaterialDetails} from '../types/material'

const isPlaying = ref(false)
const volume = ref(1)
const duration = ref<number | null>(null)
const currentTime = ref<number | null>(null)
const currentMaterialId = ref<number | null>(null)
const currentTitle = ref('')
const loading = ref(false)
const errorMessage = ref('')
let sound: Howl | null = null
let timer: ReturnType<typeof setInterval> | null = null
let currentUrl: string | null = null
let linksExpireAt: number | null = null
let requestVersion = 0
let preview: HTMLAudioElement | null = null

function stopTimer() {
  if (timer !== null) clearInterval(timer)
  timer = null
}

function playAudio(url: string, contentType?: string | null, position = 0) {
  if (currentUrl === url && sound) {
    if (!sound.playing()) sound.play()
    return
  }
  sound?.unload()
  stopTimer()
  isPlaying.value = false
  currentTime.value = position
  duration.value = null
  currentUrl = url
  const formats: Record<string, string> = {
    'audio/mpeg': 'mp3', 'audio/wav': 'wav', 'audio/x-wav': 'wav',
    'audio/mp4': 'm4a', 'audio/aac': 'aac', 'audio/ogg': 'ogg', 'audio/flac': 'flac',
  }
  const format = contentType ? formats[contentType] : undefined
  const nextSound = new Howl({
    src: [url],
    ...(format ? {format: [format]} : {}),
    volume: volume.value,
    html5: true,
    onload: () => {
      if (sound !== nextSound) return
      duration.value = nextSound.duration()
      if (position > 0) nextSound.seek(position)
    },
    onplay: () => {
      if (sound !== nextSound) return
      isPlaying.value = true
      errorMessage.value = ''
      stopTimer()
      timer = setInterval(() => {currentTime.value = nextSound.seek()}, 250)
    },
    onpause: () => {
      if (sound !== nextSound) return
      isPlaying.value = false
      stopTimer()
    },
    onend: () => {
      if (sound !== nextSound) return
      isPlaying.value = false
      currentTime.value = nextSound.duration()
      stopTimer()
    },
    onloaderror: () => playbackError(nextSound),
    onplayerror: () => playbackError(nextSound),
  })
  sound = nextSound
  nextSound.play()
}

function playbackError(failedSound: Howl) {
  if (sound !== failedSound) return
  stopTimer()
  isPlaying.value = false
  errorMessage.value = 'Не удалось воспроизвести аудио. Нажмите «Повторить».'
  currentUrl = null
}

async function playMaterial(id: number, title: string) {
  pausePreview()
  pauseAudio()
  const version = ++requestVersion
  const sameMaterial = currentMaterialId.value === id
  const savedPosition = sameMaterial ? currentTime.value ?? 0 : 0
  const position = duration.value !== null && savedPosition >= duration.value ? 0 : savedPosition
  loading.value = true
  errorMessage.value = ''
  currentMaterialId.value = id
  currentTitle.value = title
  if (!sameMaterial) {
    sound?.unload()
    sound = null
    currentUrl = null
    currentTime.value = 0
    duration.value = null
  }
  try {
    const material = await request<MaterialDetails>(`/rest-api/public/materials/${id}`)
    if (version !== requestVersion) return
    if (!material.audioUrl) throw new Error('У этого материала пока нет аудиофайла.')
    currentTitle.value = material.title
    linksExpireAt = material.linksExpireAt ? Date.parse(material.linksExpireAt) : null
    playAudio(material.audioUrl, material.audioContentType, position)
  } catch (error) {
    if (version !== requestVersion) return
    currentUrl = null
    errorMessage.value = error instanceof Error ? error.message : 'Не удалось загрузить аудио.'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function resumeAudio() {
  if (currentMaterialId.value === null || loading.value) return
  pausePreview()
  if (!currentUrl || (linksExpireAt !== null && Date.now() >= linksExpireAt)) {
    void playMaterial(currentMaterialId.value, currentTitle.value)
  } else {
    sound?.play()
  }
}

function pausePreview() {
  preview?.pause()
  preview = null
}

function playPreview(event: Event) {
  const nextPreview = event.currentTarget as HTMLAudioElement
  if (preview !== nextPreview) pausePreview()
  pauseAudio()
  preview = nextPreview
}

function pauseAudio() {
  ++requestVersion
  loading.value = false
  sound?.pause()
  isPlaying.value = false
  stopTimer()
}

function seekAudio(value: number) {
  if (!sound || duration.value === null) return
  const position = Math.max(0, Math.min(value, duration.value))
  sound.seek(position)
  currentTime.value = position
}

function formatTime(value: number | null) {
  if (value === null || !Number.isFinite(value)) return '—'
  const total = Math.max(0, Math.floor(value))
  const seconds = String(total % 60).padStart(2, '0')
  const minutes = Math.floor(total / 60)
  return minutes >= 60
    ? `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}:${seconds}`
    : `${minutes}:${seconds}`
}

export function audioService() {
  return {
    isPlaying, volume, duration, currentTime, currentMaterialId, currentTitle,
    loading, errorMessage, formatTime, playMaterial, resumeAudio, pauseAudio, playPreview, seekAudio,
  }
}
