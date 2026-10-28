<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import { authService } from '../composables/authService'
import {audioService} from '../composables/audioService'

import {
  AvatarRoot,
  AvatarImage,
  AvatarFallback,
  TabsRoot,
  TabsList,
  TabsTrigger,
  TabsContent
} from 'reka-ui'

const { user } = authService()
const {playPreview} = audioService()
const backendUrl = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '')

interface Category {
  id: number;
  name: string;
  description: string | null;
}

interface Material {
  id: number;
  title: string;
  description: string | null;
  type: string;
  status: string;
  authorName: string;
  categoryName: string;
  coverUrl: string | null;
  audioUrl: string | null;
  linksExpireAt: string | null;
}

const canUpload = computed(() => user.value.roles.some(role => role === 'AUTHOR' || role === 'ADMIN'))
const categories = ref<Category[]>([])
const materials = ref<Material[]>([])
const soundFile = ref<File | null>(null)
const coverFile = ref<File | null>(null)
const uploadForm = ref<HTMLFormElement | null>(null)
const title = ref('')
const description = ref('')
const contentType = ref('PODCAST')
const categoryId = ref('')
const loading = ref(false)
const uploading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')

const initials = computed(() => {
  if (!user.value.username) return '?'
  return user.value.username.slice(0, 2).toUpperCase()
})

function selectAudio(event: Event) {
  soundFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

function selectCover(event: Event) {
  coverFile.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(backendUrl + path, {...options, credentials: 'include'})
  const isJson = response.headers.get('content-type')?.includes('json')
  const body = isJson ? await response.json() : null

  if (!response.ok) {
    const defaultMessage = response.status === 403
        ? 'Для загрузки нужна роль AUTHOR или ADMIN.'
        : `Ошибка запроса (${response.status}).`
    throw new Error(body?.detail || body?.message || defaultMessage)
  }
  if (!isJson) {
    throw new Error('Сервер не вернул данные. Проверьте вход в аккаунт и адрес API.')
  }
  return body as T
}

function showError(error: unknown) {
  errorMessage.value = error instanceof Error ? error.message : 'Не удалось выполнить запрос.'
}

async function loadMaterials() {
  loading.value = true
  errorMessage.value = ''
  try {
    await Promise.all([
      request<Category[]>('/rest-api/public/categories').then(data => categories.value = data),
      request<Material[]>('/rest-api/author/materials').then(data => materials.value = data),
    ])
  } catch (error) {
    showError(error)
  } finally {
    loading.value = false
  }
}

watch(canUpload, allowed => {
  if (allowed) {
    void loadMaterials()
  } else {
    materials.value = []
    categories.value = []
  }
}, {immediate: true})

async function uploadAudio() {
  errorMessage.value = ''
  successMessage.value = ''
  if (!soundFile.value || !title.value.trim() || !categoryId.value) {
    errorMessage.value = 'Укажите название, категорию и аудиофайл.'
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', soundFile.value)
    if (coverFile.value) formData.append('cover', coverFile.value)
    formData.append('title', title.value.trim())
    formData.append('description', description.value.trim())
    formData.append('type', contentType.value)
    formData.append('categoryId', categoryId.value)

    const material = await request<Material>('/rest-api/author/new-material', {
      method: 'POST',
      body: formData,
    })
    materials.value.unshift(material)
    title.value = ''
    description.value = ''
    contentType.value = 'PODCAST'
    categoryId.value = ''
    soundFile.value = null
    coverFile.value = null
    uploadForm.value?.reset()
    successMessage.value = 'Материал загружен и отправлен на модерацию. Его можно прослушать ниже.'
  } catch (error) {
    showError(error)
  } finally {
    uploading.value = false
  }
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    DRAFT: 'Черновик',
    UPLOADING: 'Загрузка',
    PROCESSING: 'Обработка',
    PROCESSING_ERROR: 'Ошибка обработки',
    PENDING_MODERATION: 'На модерации',
    PUBLISHED: 'Опубликовано',
    REJECTED: 'Отклонено',
    HIDDEN: 'Скрыто',
    DELETED: 'Удалено',
  }
  return labels[status] || status
}

function expiresAt(value: string) {
  return new Date(value).toLocaleString('ru-RU')
}
</script>

<template>
  <div class="profile-page">

    <div class="profile-card">

      <div class="user-main">
        <AvatarRoot class="avatar-root">
          <AvatarImage
              class="avatar-image"
              src=""
              :alt="user.username">
          </AvatarImage>
          <AvatarFallback class="avatar-fallback">
            {{ initials }}
          </AvatarFallback>
        </AvatarRoot>

        <div class="user-details">
          <h1 class="username">{{ user.username }}</h1>
          <div class="roles-list">
            <span
                v-for="role in user.roles"
                :key="role"
                class="role-badge">
              {{ role }}
            </span>
          </div>
        </div>

      </div>
    </div>

    <TabsRoot default-value="general" class="tabs-root">
      <TabsList class="tabs-list">
        <TabsTrigger value="general" class="tabs-trigger">
          Общие данные
        </TabsTrigger>
        <TabsTrigger value="podcasts" class="tabs-trigger">
          Мои подкасты
        </TabsTrigger>
        <TabsTrigger value="history" class="tabs-trigger">
          История
        </TabsTrigger>
      </TabsList>

      <TabsContent value="general" class="tabs-content">
        <div class="info-grid">
          <div class="info-item">
            <span class="label">Имя пользователя</span>
            <span class="value">{{ user.username }}</span>
          </div>
          <div class="info-item">
            <span class="label">Личные данные</span>
            <span class="value">{{ user.lastName + " " + user.firstName }}</span>
          </div>
          <div class="info-item">
            <span class="label">Email</span>
            <span class="value">{{ user.email }}</span>
          </div>
        </div>
      </TabsContent>

      <TabsContent value="podcasts" class="tabs-content">
        <div v-if="!canUpload" class="empty-state">
          <p>Для загрузки аудиоматериалов нужна роль автора (AUTHOR). Обратитесь к администратору.</p>
        </div>
        <template v-else>
          <p v-if="errorMessage" class="error-message" role="alert">{{ errorMessage }}</p>
          <p v-if="successMessage" class="success-message" role="status">{{ successMessage }}</p>
          <p v-if="loading">Загрузка категорий и материалов…</p>
          <p v-else-if="!categories.length" class="empty-state">
            Категории пока не созданы. Попросите администратора добавить категорию и обновите список.
          </p>

          <form ref="uploadForm" class="uploader-container" @submit.prevent="uploadAudio">
            <h2>Новый аудиоматериал</h2>
            <label>
              Название
              <input v-model="title" type="text" maxlength="255" required :disabled="uploading" />
            </label>
            <label>
              Описание
              <textarea v-model="description" rows="3" :disabled="uploading"></textarea>
            </label>
            <label>
              Тип материала
              <select v-model="contentType" :disabled="uploading">
                <option value="PODCAST">Подкаст</option>
                <option value="AUDIOBOOK">Аудиокнига</option>
              </select>
            </label>
            <label>
              Категория
              <select v-model="categoryId" required :disabled="uploading || loading">
                <option value="" disabled>Выберите категорию</option>
                <option v-for="category in categories" :key="category.id" :value="String(category.id)">
                  {{ category.name }}
                </option>
              </select>
            </label>
            <label>
              Аудиофайл (до 100 МБ)
              <input type="file" accept=".mp3,.wav,.m4a,.aac,.ogg,.flac" required :disabled="uploading" @change="selectAudio" />
            </label>
            <label>
              Обложка (JPEG, PNG или WebP до 5 МиБ, необязательно)
              <input type="file" accept="image/jpeg,image/png,image/webp" :disabled="uploading" @change="selectCover" />
            </label>
            <button class="upload-button" type="submit" :disabled="uploading || loading || !categories.length">
              {{ uploading ? 'Загружаем…' : 'Загрузить и отправить на модерацию' }}
            </button>
          </form>

          <div class="materials-heading">
            <h2>Мои аудиоматериалы</h2>
            <button class="upload-button" type="button" :disabled="loading || uploading" @click="loadMaterials">Обновить список</button>
          </div>
          <p v-if="!loading && !materials.length" class="empty-state">Вы пока не загрузили ни одного аудиоматериала.</p>
          <article v-for="material in materials" :key="material.id" class="material-card">
            <img v-if="material.coverUrl" :src="material.coverUrl" :alt="'Обложка: ' + material.title" class="material-cover" />
            <div class="material-info">
              <h3>{{ material.title }}</h3>
              <p>{{ material.authorName }} · {{ material.categoryName }}</p>
              <p>{{ material.type === 'AUDIOBOOK' ? 'Аудиокнига' : 'Подкаст' }} · {{ statusLabel(material.status) }}</p>
              <p v-if="material.description">{{ material.description }}</p>
              <audio v-if="material.audioUrl" :key="material.audioUrl" :src="material.audioUrl" controls preload="metadata" @play="playPreview"></audio>
              <p v-else>Аудио пока недоступно.</p>
              <p v-if="material.linksExpireAt" class="link-expiration">Ссылки действуют до {{ expiresAt(material.linksExpireAt) }}</p>
            </div>
          </article>
        </template>
      </TabsContent>

      <TabsContent value="history" class="tabs-content">
        <div class="empty-state">
          <p>История прослушиваний пуста.</p>
        </div>
      </TabsContent>
    </TabsRoot>
  </div>
</template>

<style scoped>
/* главный блок */
.profile-page {
  padding: 32px; /* внутренние отступы */
  max-width: 800px;
  margin: 0 auto;
}



/* карточка пользователя */
.profile-card {
  background-color: #2E303A; /* цвет фона */
  border-radius: 12px; /* округление */
  padding: 24px; /* внутренние отступы */
  display: flex; /* построение в ряд */
  justify-content: space-between; /* место между объектами */
  align-items: center; /* выравнивание по вертикали по центру */
  margin-bottom: 24px;
}

.user-main {
  display: flex; /* построение в ряд */
  align-items: center;
  gap: 16px;
}

.username {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 8px;
  margin-top: 8px;
}

.role-badge {
  background-color: #424453; /* цвет фона */
  border-radius: 12px; /* округление */
  padding: 4px 10px;
  font-size: 12px;
  margin-right: 6px;
}



/* аватарка */
.avatar-root {
  width: 64px;
  height: 64px;
  border-radius: 50%; /* круглая форма */
  background-color: #424453; /* цвет фона */
  display: flex;
  align-items: center; /* выравнивание по вертикали по центру */
  justify-content: center;
  font-weight: bold;
  font-size: 20px;
  color: white;
}

.avatar-image {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}


/* вкладки */
.tabs-list {
  display: flex; /* построение в ряд */
  gap: 8px;
  border-bottom: 1px solid #2E303AFF;
}

.tabs-trigger {
  background: transparent;
  border: none;
  color: gray;
  padding: 10px 16px;
  cursor: pointer; /* смена курсора при наведении */
  font-weight: bold;
}

.tabs-trigger[data-state='active'] {
  color: white;
  border-bottom: 2px solid white;
}



/* содержимое вкладок */
.tabs-content {
  background-color: #2E303A; /* цвет фона */
  border-radius: 12px; /* округление */
  padding: 24px; /* внутренние отступы */
  margin-top: 16px;
}

.info-item {
  display: flex; /* построение в ряд */
  justify-content: space-between; /* место между объектами */
  padding: 12px 0;
  border-bottom: 1px solid #424453;
}

.info-item:last-child {
  border-bottom: none;
}

.label {
  color: gray;
}

.empty-state {
  text-align: center;
  color: gray;
  padding: 24px 0;
}


/* загрузка файлов */
.uploader-container {
  width: 100%;
  display: grid;
  gap: 14px;
  text-align: left;
}

.uploader-container label {
  display: grid;
  gap: 6px;
}

.uploader-container input,
.uploader-container select,
.uploader-container textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #636575;
  border-radius: 6px;
  padding: 8px;
  background: #20222b;
  color: #f3f4f6;
  font: inherit;
}

.upload-button {
  padding: 10px 14px;
  border: 1px solid #636575;
  border-radius: 6px;
  background: #424453;
  color: #f3f4f6;
  cursor: pointer;
}

.upload-button:disabled {
  opacity: 0.6;
  cursor: default;
}

.error-message,
.success-message {
  margin-bottom: 16px;
  overflow-wrap: anywhere;
}

.error-message {
  color: #ffaaa5;
}

.success-message {
  color: #a5e1b5;
}

.materials-heading {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-top: 32px;
}

.material-card {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 20px 0;
  border-bottom: 1px solid #424453;
  text-align: left;
}

.material-cover {
  width: 100px;
  height: 100px;
  object-fit: cover;
  border-radius: 8px;
}

.material-info {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}

.material-info h3 {
  margin: 0 0 8px;
}

.material-info p {
  margin-bottom: 8px;
}

.material-info audio {
  display: block;
  width: 100%;
  margin: 12px 0;
}

.link-expiration {
  font-size: 12px;
}
</style>
