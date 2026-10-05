<script setup lang="ts">

import {ref} from "vue";

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
      <div v-for="card in cards" :key="card.id" class="podcast-card">
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
  </main>
</template>

<style scoped>

</style>