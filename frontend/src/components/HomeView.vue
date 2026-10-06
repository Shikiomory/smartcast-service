<script setup lang="ts">

import { ref } from "vue";
import { audioService } from "../composables/audioService.ts";
const {isPlaying, duration, currentTime, playAudio, pauseAudio} = audioService();

const soundFile = ref()

interface Card {
  id: number
  title: string
  author: string
  duration: string
}


const cards = ref<Card[]>([
  { id: 1, title: 'Инквизитор Эйзенхорн - лучший цикл книг по Вархаммер 40.000', author: 'Практическая светлая магия', duration: '11:35'},
  { id: 2, title: 'Всё о Java: экосистема, популярные фреймворки, системы сборки, JDK, JVM и будущее языка', author: 'Люди и код', duration: '1:13:34'},
  { id: 3, title: 'Михаил Ершлов. Зачем пивовару нюхать скунса? [18+]', author: 'Два пива, пжлст!', duration: '1:09:40'},
  { id: 4, title: 'Как учат английский по методам спецслужб? Секретный выпуск из архивов!', author: 'Без языка', duration: '25:49'},
])

function handleCardClick(card: Card) {
  console.log(card);
  // playAudio("https://apostol-space.tech/uploads/404a8e76b218762e9efe38108f16e080_e2a7d1be93b1ad3a6c036e7f6597d6c6.mp3")
  playAudio("/ost.mp3")
}

function togglePlayState() {
  if (isPlaying.value) {
    pauseAudio();
  }
  else {
    // playAudio("https://apostol-space.tech/uploads/404a8e76b218762e9efe38108f16e080_e2a7d1be93b1ad3a6c036e7f6597d6c6.mp3");
    playAudio("/ost.mp3")
  }
}

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
  <header>
    <div class="logo-container">
      <h1>SmartCast</h1>
    </div>

    <div class="search-container">
      <input type="text" placeholder="Поиск..." />
    </div>

    <div class="auth-container">
      <button>Войти</button>
      <button>Регистрация</button>
    </div>
  </header>

  <main>
    <div class="content">
      <div v-for="card in cards" :key="card.id" class="card" @click="handleCardClick(card)">
        <div class="card-cover">
<!--          <img class="card-img" :src="card.image" />-->
          <h2>Обложка</h2>
        </div>
        <div class="card-content">
          <h3 class="card-title">{{ card.title }}</h3>
          <p class="card-author">{{ card.author }}</p>
          <span class="card-duration">{{ card.duration }}</span>
        </div>
      </div>
    </div>

    <div class="uploader-container">
      <input type="file" @change="uploadFile"/>
      <button class="upload-button" @click="uploadAudio"> Загрузить </button>
    </div>
  </main>

  <footer class="footer-player">
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


/* главный блок */
main {
  padding: 32px 32px; /* внутренние отступы */
}

.content {
  display: grid; /* сетка */
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); /* автоматическое построение колонок по размеру */
  gap: 24px; /* отступы между карточками */
}



/* карточки */
.card {
  background-color: #2E303A; /* цвет фона */
  border-radius: 12px; /* округление */
  padding: 16px; /* внутренние отступы */
  cursor: pointer; /* смена курсора при наведении */
  transition: background-color 0.3s ease; /* затемнение при наведении */
}

.card:hover {
  background-color: #424453; /* затемнение при наведении */
}

.card-cover {

}

.card-title {
  font-size: 18px;
  font-weight: bold;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-author {
  font-size: 16px;
  margin-bottom: 4px;
}

.card-duration {
  font-size: 14px;
  color: gray;
}



/* загрузка файлов */
.uploader-container {
  width: 100%;
  padding: 32px;
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