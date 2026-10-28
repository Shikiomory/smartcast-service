<script setup lang="ts">

import { FileAudio, Play, Pause} from "@lucide/vue";
import {onMounted, ref} from "vue";
import { audioService } from "../composables/audioService.ts";
import { authService } from "../composables/authService.ts";
import {CardDescription, CardFooter, CardHeader, CardTitle} from "@/components/ui/card";
const {playPauseAudio} = audioService();
const { authStatus } = authService()

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
  playPauseAudio("/ost.mp3")
}

async function uploadAudio() {
  try {
    const response = await fetch("")
    if (response.ok) {

    }
  } catch (error) {
    console.log(error)
  }
}

async function uploadAudio() {
  try {
    const response = await fetch("")
    if (response.ok) {

    }
  } catch (error) {
    console.log(error)
  }
}

onMounted(() => {
  authStatus()
  uploadAudio()
})
</script>

<template>
  <div class="w-full max-w-7xl mx-auto p-6 md:p-8">
    <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
      <Card v-for="card in cards" :key="card.id" class="cursor-pointer overflow-hidden rounded-lg group flex flex-col transition-colors duration-200 border hover:border-primary hover:bg-accent/30" @click="handleCardClick(card)">
        <div class="relative aspect-square w-full bg-muted flex items-center justify-center overflow-hidden border-b ">
          <FileAudio class="size-12 text-muted-foreground transition-transform duration-300 group-hover:scale-105" />

          <div class="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200">
            <Play class="size-8 text-white fill-white drop-shadow" />
          </div>
        </div>

        <CardHeader class="p-4 pb-2 space-y-1 flex-1">
          <CardTitle class="text-base leading-snug line-clamp-2 group-hover:text-primary transition-colors"> {{ card.title }}</CardTitle>
          <CardDescription> {{ card.author }}</CardDescription>
        </CardHeader>
        <CardFooter class="p-4 pt-0 text-xs text-muted-foreground flex items-center gap-1.5">
          <time class="font-mono"> {{ card.duration }}</time>
        </CardFooter>
      </Card>
    </div>
  </div>
</template>