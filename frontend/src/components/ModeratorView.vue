<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {authService} from '../composables/authService'
import {audioService} from '../composables/audioService'
import {ApiError, request} from '../composables/api'
import type {Material, MaterialDetails} from '../types/material'

const {user} = authService()
const {playPreview} = audioService()
const canModerate = computed(() => user.value.isAuthenticated
    && user.value.roles.some(role => role === 'MODERATOR' || role === 'ADMIN'))
const materials = ref<Material[]>([])
const viewStatus = ref<'PENDING_MODERATION' | 'PUBLISHED'>('PENDING_MODERATION')
const publishedView = computed(() => viewStatus.value === 'PUBLISHED')
const selected = ref<MaterialDetails | null>(null)
const loading = ref(false)
const detailsLoading = ref(false)
const deciding = ref(false)
const accessDenied = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const audioError = ref(false)
let queueRequest = 0
let detailsRequest = 0
let decisionRequest = 0

function clearRequests() {
  queueRequest++
  detailsRequest++
  decisionRequest++
  materials.value = []
  selected.value = null
  loading.value = false
  detailsLoading.value = false
  deciding.value = false
}

function showError(error: unknown) {
  if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
    clearRequests()
    accessDenied.value = true
    errorMessage.value = error.status === 401
        ? 'Сессия завершилась. Войдите в аккаунт модератора ещё раз.'
        : 'Нет доступа к модерации. Нужна роль MODERATOR или ADMIN.'
    return
  }
  errorMessage.value = error instanceof Error ? error.message : 'Не удалось выполнить запрос.'
}

async function loadQueue(clearError = true) {
  if (!canModerate.value || accessDenied.value) return
  const version = ++queueRequest
  detailsRequest++
  selected.value = null
  detailsLoading.value = false
  loading.value = true
  if (clearError) errorMessage.value = ''
  try {
    const result = await request<Material[]>(`/rest-api/moderator/materials?status=${viewStatus.value}`)
    if (version === queueRequest) materials.value = result
  } catch (error) {
    if (version === queueRequest) showError(error)
  } finally {
    if (version === queueRequest) loading.value = false
  }
}

async function openMaterial(id: number) {
  if (!canModerate.value || accessDenied.value || deciding.value) return
  const version = ++detailsRequest
  detailsLoading.value = true
  selected.value = null
  audioError.value = false
  errorMessage.value = ''
  successMessage.value = ''
  try {
    const details = await request<MaterialDetails>(`/rest-api/moderator/materials/${id}`)
    if (version !== detailsRequest) return
    if (details.status !== viewStatus.value) {
      throw new ApiError(409, 'Статус материала уже изменился.')
    }
    selected.value = details
  } catch (error) {
    if (version !== detailsRequest) return
    if (error instanceof ApiError && (error.status === 404 || error.status === 409)) {
      materials.value = materials.value.filter(material => material.id !== id)
      errorMessage.value = 'Статус материала уже изменился или запись удалена. Обновите список.'
    } else {
      showError(error)
    }
  } finally {
    if (version === detailsRequest) detailsLoading.value = false
  }
}

async function decide(action: 'approve' | 'disapprove' | 'unpublish') {
  if (!selected.value || deciding.value || !canModerate.value || accessDenied.value) return
  const material = selected.value
  const version = detailsRequest
  const decisionVersion = ++decisionRequest
  deciding.value = true
  errorMessage.value = ''
  successMessage.value = ''
  try {
    await request<Material>(`/rest-api/moderator/${action}/${material.id}`, {
      method: 'POST',
    })
    if (version !== detailsRequest) return
    materials.value = materials.value.filter(item => item.id !== material.id)
    selected.value = null
    const messages = {
      approve: `«${material.title}» опубликован. Теперь запись доступна на главной странице.`,
      disapprove: `«${material.title}» отклонён. Автор увидит новый статус в своём профиле.`,
      unpublish: `«${material.title}» снят с публикации и возвращён на проверку. Запись больше не доступна в публичном каталоге.`,
    }
    successMessage.value = messages[action]
  } catch (error) {
    if (version !== detailsRequest) return
    if (error instanceof ApiError && (error.status === 404 || error.status === 409)) {
      errorMessage.value = 'Материал уже обработан или удалён. Очередь обновлена.'
      await loadQueue(false)
    } else {
      showError(error)
    }
  } finally {
    if (decisionVersion === decisionRequest) deciding.value = false
  }
}

watch(() => [canModerate.value, user.value.id, user.value.username, viewStatus.value], () => {
  clearRequests()
  accessDenied.value = false
  errorMessage.value = ''
  successMessage.value = ''
  if (canModerate.value) void loadQueue()
}, {immediate: true})

onBeforeUnmount(clearRequests)
</script>

<template>
  <section class="moderation-page">
    <div class="page-heading">
      <div>
        <h1>Модерация</h1>
        <p>Проверяйте новые записи и управляйте опубликованными материалами.</p>
      </div>
      <button v-if="canModerate && !accessDenied" type="button" :disabled="loading || detailsLoading || deciding" @click="loadQueue()">
        {{ loading ? 'Обновляем…' : 'Обновить список' }}
      </button>
    </div>

    <p v-if="errorMessage" class="error-message" role="alert">{{ errorMessage }}</p>
    <p v-if="successMessage" class="success-message" role="status">{{ successMessage }}</p>
    <p v-if="!canModerate" class="empty-state">Для проверки материалов нужна роль MODERATOR или ADMIN.</p>
    <template v-else-if="!accessDenied">
      <div class="status-tabs" aria-label="Статус материалов">
        <button type="button" :aria-pressed="!publishedView" :disabled="deciding"
                @click="viewStatus = 'PENDING_MODERATION'">На проверке</button>
        <button type="button" :aria-pressed="publishedView" :disabled="deciding"
                @click="viewStatus = 'PUBLISHED'">Опубликованные</button>
      </div>
      <p v-if="loading" role="status">Загрузка материалов…</p>
      <p v-else-if="!materials.length" class="empty-state">{{ publishedView ? 'Опубликованных материалов пока нет.' : 'Материалов на проверке пока нет.' }}</p>
      <div v-else class="moderation-layout">
        <aside class="queue" :aria-label="publishedView ? 'Опубликованные материалы' : 'Материалы на проверке'">
          <h2>{{ publishedView ? 'Опубликовано' : 'Ожидают проверки' }}: {{ materials.length }}</h2>
          <ul>
            <li v-for="material in materials" :key="material.id">
              <button class="queue-item" type="button" :class="{selected: selected?.id === material.id}"
                      :aria-pressed="selected?.id === material.id" :disabled="detailsLoading || deciding"
                      @click="openMaterial(material.id)">
                <strong>{{ material.title }}</strong>
                <span>{{ material.authorName }} · {{ material.categoryName }}</span>
              </button>
            </li>
          </ul>
        </aside>

        <div class="preview">
          <p v-if="detailsLoading" role="status">Загрузка записи…</p>
          <p v-else-if="!selected" class="empty-state">Выберите материал из очереди.</p>
          <article v-else>
            <img v-if="selected.coverUrl" :src="selected.coverUrl" :alt="'Обложка: ' + selected.title" class="material-cover" />
            <h2>{{ selected.title }}</h2>
            <p>{{ selected.authorName }} · {{ selected.categoryName }}</p>
            <p>{{ selected.type === 'AUDIOBOOK' ? 'Аудиокнига' : 'Подкаст' }} · {{ publishedView ? 'Опубликовано' : 'На модерации' }}</p>
            <p class="description">{{ selected.description || 'Описание не добавлено.' }}</p>
            <audio v-if="selected.audioUrl" :key="selected.audioUrl" :src="selected.audioUrl"
                   controls preload="metadata" @play="playPreview" @error="audioError = true"></audio>
            <p v-else class="error-message">Аудио пока недоступно. Обновите страницу перед проверкой.</p>
            <p v-if="audioError" class="error-message" role="alert">Не удалось воспроизвести аудио. Обновите страницу и попробуйте снова.</p>
            <div class="decision-actions">
              <button v-if="publishedView" type="button" :disabled="deciding" @click="decide('unpublish')">
                {{ deciding ? 'Сохраняем…' : 'Снять с публикации' }}
              </button>
              <template v-else>
                <button class="approve-button" type="button" :disabled="deciding || !selected.audioUrl" @click="decide('approve')">
                  {{ deciding ? 'Сохраняем…' : 'Одобрить и опубликовать' }}
                </button>
                <button type="button" :disabled="deciding" @click="decide('disapprove')">Отклонить</button>
              </template>
            </div>
            <p v-if="publishedView" class="withdraw-hint">Снятая запись вернётся на проверку. Аудиофайл сохранится для повторного рассмотрения.</p>
          </article>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.moderation-page { padding: 32px; text-align: left; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; margin-bottom: 24px; }
.page-heading h1 { font-size: 30px; letter-spacing: normal; margin: 0 0 8px; }
.status-tabs { display: flex; gap: 10px; margin-bottom: 24px; flex-wrap: wrap; }
.status-tabs button[aria-pressed='true'] { border-color: var(--accent); background: var(--accent-bg); }
.withdraw-hint { font-size: 14px; }
.moderation-layout { display: grid; grid-template-columns: minmax(230px, 1fr) minmax(0, 2fr); gap: 24px; }
.queue h2 { font-size: 19px; }
.queue ul { display: grid; gap: 10px; list-style: none; padding: 0; }
button { border: 1px solid var(--border); border-radius: 8px; padding: 10px 14px; background: var(--code-bg); color: var(--text-h); font: inherit; cursor: pointer; }
button:hover:not(:disabled) { border-color: var(--accent); }
button:disabled { opacity: 0.6; cursor: default; }
button:focus-visible { outline: 2px solid var(--accent); outline-offset: 3px; }
.queue-item { display: grid; gap: 6px; width: 100%; text-align: left; overflow-wrap: anywhere; }
.queue-item span { font-size: 14px; color: var(--text); }
.queue-item.selected { border-color: var(--accent); background: var(--accent-bg); }
.preview { border: 1px solid var(--border); border-radius: 12px; padding: 24px; overflow-wrap: anywhere; }
.preview article { display: grid; gap: 14px; }
.preview h2 { margin: 0; }
.material-cover { width: 160px; max-width: 100%; height: 160px; object-fit: cover; border-radius: 8px; }
.description { white-space: pre-wrap; }
audio { width: 100%; }
.decision-actions { display: flex; flex-wrap: wrap; gap: 12px; padding-top: 12px; border-top: 1px solid var(--border); }
.approve-button { background: var(--accent-bg); border-color: var(--accent-border); }
.empty-state { padding: 20px 0; }
.error-message, .success-message { margin-bottom: 16px; overflow-wrap: anywhere; }
.error-message { color: #c84646; }
.success-message { color: #278447; }
@media (prefers-color-scheme: dark) { .error-message { color: #ffaaa5; } .success-message { color: #a5e1b5; } }
@media (max-width: 700px) { .moderation-page { padding: 20px; } .moderation-layout { grid-template-columns: 1fr; } .preview { padding: 16px; } }
</style>
