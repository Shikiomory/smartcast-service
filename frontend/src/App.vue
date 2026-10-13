<script setup lang="ts">
// import HelloWorld from './components/HelloWorld.vue'
// import { RouterView, RouterLink } from 'vue-router'
import {authService} from "./composables/authService"
import { audioService } from "./composables/audioService.ts";
const {login, registration, logout, isAuthenticated} = authService()
const {isPlaying, duration, currentTime, playAudio, pauseAudio} = audioService();

function togglePlayState() {
  if (isPlaying.value) {
    pauseAudio();
  }
  else {
    // playAudio("https://apostol-space.tech/uploads/404a8e76b218762e9efe38108f16e080_e2a7d1be93b1ad3a6c036e7f6597d6c6.mp3");
    playAudio("/ost.mp3")
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
      <RouterLink to="/profile" class="nav-item">Профиль</RouterLink>
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


  <footer v-if="duration" class="footer-player">
    <button class="play-pause-button" @click="togglePlayState"> {{isPlaying ? 'Пауза' : 'Продолжить'}}</button>
    <span class="audio-duration"> {{ currentTime + '/' + duration }} </span>
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



/* плеер */
.footer-player {
  position: fixed;
  bottom: 5%;
  left: 18%;
  width: 60%;
  height: 60px;
  border-radius: 20px;
  background-color: gray;
  display: flex;
  padding: 16px 32px;
  align-items: center; /* выравнивание по вертикали по центру */
  gap: 24px;
}

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
</style>