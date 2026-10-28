<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {audioService} from '../composables/audioService'
import {request} from '../composables/api'
import type {Material} from '../types/material'

const {playMaterial, currentMaterialId, isPlaying, loading: audioLoading, formatTime} = audioService()
const materials = ref<Material[]>([])
const loading = ref(false)
const errorMessage = ref('')

async function loadMaterials() {
  loading.value = true
  errorMessage.value = ''
  try {
    materials.value = await request<Material[]>('/rest-api/public/materials')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : 'Не удалось загрузить каталог.'
  } finally {
    loading.value = false
  }
}

onMounted(loadMaterials)
</script>

<template>
  <section class="catalog">
    <div class="catalog-heading">
      <div>
        <h2>Каталог аудиоматериалов</h2>
        <p>Подкасты и аудиокниги, прошедшие модерацию.</p>
      </div>
      <button type="button" :disabled="loading" @click="loadMaterials">Обновить</button>
    </div>
    <p v-if="loading" role="status">Загружаем каталог…</p>
    <p v-else-if="errorMessage" class="error-message" role="alert">{{ errorMessage }}</p>
    <p v-else-if="!materials.length" class="empty-state">Опубликованных материалов пока нет. Они появятся здесь после одобрения модератором.</p>
    <div v-else class="content">
      <article v-for="material in materials" :key="material.id" class="card">
        <img v-if="material.coverUrl" class="card-cover" :src="material.coverUrl" :alt="'Обложка: ' + material.title" />
        <div v-else class="cover-placeholder" aria-hidden="true">♫</div>
        <div class="card-content">
          <p class="card-category">{{ material.categoryName }} · {{ material.type === 'AUDIOBOOK' ? 'Аудиокнига' : 'Подкаст' }}</p>
          <h3>{{ material.title }}</h3>
          <p>{{ material.authorName }}</p>
          <p v-if="material.description" class="description">{{ material.description }}</p>
          <p v-if="material.durationMs !== null">{{ formatTime(material.durationMs / 1000) }}</p>
          <button type="button" :disabled="audioLoading && currentMaterialId === material.id"
                  @click="playMaterial(material.id, material.title)">
            {{ currentMaterialId === material.id && audioLoading ? 'Загружаем…' : currentMaterialId === material.id && isPlaying ? 'Играет' : 'Слушать' }}
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.catalog {padding: 32px; text-align: left;}
.catalog-heading {display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 24px;}
.content {display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 24px;}
.card {background: var(--code-bg); border: 1px solid var(--border); border-radius: 12px; overflow: hidden;}
.card-cover, .cover-placeholder {width: 100%; height: 190px; object-fit: cover;}
.cover-placeholder {display: grid; place-items: center; background: var(--accent-bg); font-size: 48px;}
.card-content {display: grid; gap: 10px; padding: 16px; overflow-wrap: anywhere;}
h3 {margin: 0; color: var(--text-h); font-size: 18px;}
.card-category {font-size: 13px;}
.description {display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; white-space: pre-line;}
button {padding: 10px 16px; border: 1px solid var(--accent-border); border-radius: 8px; background: var(--accent-bg); color: var(--text-h); font: inherit; cursor: pointer;}
button:disabled {opacity: .6; cursor: wait;}
.error-message {color: #d65353;}
.empty-state {padding: 24px 0;}
@media (max-width: 600px) {.catalog {padding: 20px;}}
</style>
