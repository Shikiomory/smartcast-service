<script setup lang="ts">
import {computed, ref} from 'vue'
import { authService } from '../composables/authService'

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
const soundFile = ref()


const initials = computed(() => {
  if (!user.value.username) return '?'
  return user.value.username.slice(0, 2).toUpperCase()
})

function uploadFile(event: Event) {
  const target = event.target as HTMLInputElement;

  if (target.files && target.files[0]) {
    soundFile.value = target.files[0]
    console.log("Файл загружен с диска:" + soundFile.value.name)
  }

}

async function uploadAudio() {
  try {
    if (!soundFile.value) {
      alert("Файл не выбран")
      return;
    }

    const formData = new FormData();

    formData.append("file", soundFile.value);

    const response = await fetch("/rest-api/author/new-material",
        {
          method: "POST",
          body: formData
        })

    if (response.ok) {
      soundFile.value = null;
      alert("Файл загружен")
    }
  } catch (error) {
    console.log("Ошибка при загрузке файла: " + error)
  }
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
        <div class="empty-state">
          <p>Вы пока не загрузили ни одного подкаста.</p>
        </div>

        <div class="uploader-container">
          <input type="file" @change="uploadFile"/>
          <button class="upload-button" @click="uploadAudio"> Загрузить </button>
        </div>
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
}
</style>