<script setup lang="ts">
// import HelloWorld from './components/HelloWorld.vue'
import { RouterView, RouterLink } from 'vue-router'
import { SliderRoot, SliderTrack, SliderRange, SliderThumb } from "reka-ui";
import { authService } from "./composables/authService"
import { audioService } from "./composables/audioService";
import {computed, onMounted} from "vue";
const { login, registration, logout, isAuthenticated, user, authStatus } = authService()
const canModerate = computed(() => user.value.isAuthenticated && user.value.roles.some(role => role === 'MODERATOR' || role === 'ADMIN'))
const { isPlaying, duration, currentTime, currentMaterialId, currentTitle, loading, errorMessage, formatTime, resumeAudio, pauseAudio, seekAudio } = audioService();
onMounted(authStatus)

const progressValue = computed({
  get() {
    return [currentTime.value ?? 0]
  },

  set(value: number[]) {
    if (value[0] !== undefined) {
      seekAudio(value[0])
    }
  }
})

function togglePlayState() {
  if (isPlaying.value) {
    pauseAudio();
  }
  else {
    resumeAudio()
  }
}


</script>

<template>
<!--  <HelloWorld />-->
  <header>
    <div class="logo-container">
      <h1>SmartCast</h1>
    </div>

    <div class="search-container">
      <input type="text" placeholder="Поиск..." />
    </div>

    <nav class="navigation">
      <RouterLink to="/" class="nav-item">Главная</RouterLink>
      <RouterLink v-if="isAuthenticated()" to="/profile" class="nav-item">Профиль</RouterLink>
      <RouterLink v-if="canModerate" to="/moderation" class="nav-item">Модерация</RouterLink>
    </nav>

    <div class="auth-container">
      <button v-if="!isAuthenticated()" class="authorization-button" @click="login">Войти</button>
      <button v-if="!isAuthenticated()" class="registration-button" @click="registration">Регистрация</button>
      <button v-if="isAuthenticated()" class="logout-button" @click="logout">Выйти</button>
    </div>
  </header>

  <main class="main-content">
    <router-view/>
  </main>


  <footer v-if="currentMaterialId !== null" class="footer-player" aria-label="Аудиоплеер">
    <div class="player-heading">
      <span class="player-title">{{ currentTitle }}</span>
      <p v-if="errorMessage" class="player-error" role="alert">{{ errorMessage }}</p>
      <p v-else-if="loading" role="status">Загружаем аудио…</p>
    </div>
    <button class="play-pause-button" :disabled="loading" :aria-label="isPlaying ? 'Пауза' : 'Продолжить воспроизведение'" @click="togglePlayState">{{ errorMessage ? 'Повторить' : isPlaying ? '⏸' : '▶' }}</button>
    <span class="audio-duration"> {{ formatTime(currentTime) + "/" + formatTime(duration)  }} </span>

    <SliderRoot
      v-model="progressValue"
      :max="duration || 1"
      :disabled="!duration || loading"
      :step="1"
      class="slider-root"
    >
      <SliderTrack class="slider-track">
        <SliderRange class="slider-range"/>
      </SliderTrack>
      <SliderThumb class="slider-thumb"/>
    </SliderRoot>
  </footer>
</template>



<style scoped>
/* заголовок */
header {
  display: flex; /* построение в ряд */
  justify-content: space-between; /* место между объектами */
  align-items: center; /* выравнивание по вертикали по центру */
  padding: 16px 32px; /* внутренние отступы */
  border-bottom: 1px solid #2E303AFF;
  flex-wrap: wrap;
  gap: 16px;
}

.search-container input {
  padding: 8px 16px; /* внутренние отступы */
  width: 300px; /* ширина */
  border-radius: 20px; /* округление */
  border: 1px solid gray; /* обводка */

}

.auth-container button {
  margin-left: 8px;
  padding: 8px 16px; /* внутренние отступы */
  cursor: pointer; /* смена курсора при наведении */
  border-radius: 20px; /* округление */
  border: 1px solid gray; /* обводка */
  font-weight: bold; /* жирный шрифт */
}

.auth-container button:hover {
  background-color: gray; /* затемнение при наведении */
}

.navigation {
  display: flex;
  gap: 16px; /* или margin-right: 16px у ссылок */
}

.navigation a {
  color: #94a3b8;
  text-decoration: none;
}

.navigation a:hover {
  color: white;
}



/* плеер */
.footer-player {
  position: fixed;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
  width: min(1000px, calc(100% - 32px));
  box-sizing: border-box;
  border-radius: 20px;
  background-color: gray;
  display: flex;
  padding: 16px 32px;
  align-items: center; /* выравнивание по вертикали по центру */
  gap: 24px;
  flex-wrap: wrap;
  z-index: 10;
}

.main-content {padding-bottom: 180px;}
.player-heading {width: 100%; text-align: left; overflow-wrap: anywhere; color: white;}
.player-title {font-weight: 600;}
.player-error {color: #ffe0dc;}
.footer-player .slider-root {flex: 1; min-width: 100px;}
.play-pause-button:disabled {opacity: .6;}

.play-pause-button {
  cursor: pointer;
  font-weight: bold;
  border-radius: 20px;
  padding: 8px 16px;
}

.play-pause-button:hover {
  background-color: gray; /* затемнение при наведении */
}

.audio-duration {
  color: white;
}

.slider-root {
  width: 100%;
  height: 20px;
  cursor: pointer;
  user-select: none;
  touch-action: none;
  position: relative;
  display: flex;
  align-items: center;
}

.slider-track {
  background-color: #424453;
  height: 6px;
  flex-grow: 1;
  position: relative;
  border-radius: 20px;
}

.slider-range {
  position: absolute;
  height: 100%;
  background-color: white;
  border-radius: 20px;
}

.slider-thumb {
  display: block;
  width: 14px;
  height: 12px;
  background-color: white;
  border-radius: 35%;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
  transition: transform 0.1s ease;
}

.slider-thumb:hover {
  transform: scale(1.2);
}
</style>
