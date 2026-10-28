<script setup lang="ts">
// import HelloWorld from './components/HelloWorld.vue'
import {Play, Pause, Search, LogOut} from "@lucide/vue"
import { RouterView, RouterLink } from 'vue-router'
import { SliderRoot, SliderTrack, SliderRange, SliderThumb } from "reka-ui"
import { authService } from "./composables/authService"
import { audioService } from "./composables/audioService"
import {computed} from "vue"

import { Slider,} from "@/components/ui/slider"
import {Input} from "@/components/ui/input";
import {Button} from "@/components/ui/button";

const { login, registration, logout, isAuthenticated } = authService()
const { isPlaying, duration, currentTime, formatTime, playPauseAudio, seekAudio } = audioService();

const progressValue = computed({
  get() {
    return [currentTime.value ?? 0]
  },

  set(value: number[]) {
    if (value) {
      seekAudio(value[0])
    }
  }
})

</script>

<template>

  <div class="min-h-screen flex flex-col bg-background text-foreground">
    <header class="sticky top-0 z-40 w-full border-b bg-background">
      <div class="container flex h-16 items-center justify-between gap-4 px-4 sm:px-8 mx-auto max-w-7xl">
        <div>
          <RouterLink to="/" class="flex items-center gap-2 font-bold text-2xl tracking-tight transition-opacity hover:opacity-90">
            <span>Smartcast</span>
          </RouterLink>
        </div>

        <div class="relative w-full max-w-xs hidden:md:block">
          <Search class="absolute left-3 top-1/2 -translate-y-1/2 size-4 text-muted-foreground" />
          <Input type="search" placeholder="Поиск..." class="pl-9 h-9 w-full bg-muted/40, rounded-full"/>
        </div>

        <nav class="flex items-center gap-6 text-sm font-medium">
          <RouterLink to="/" class="text-muted-foreground transition-colors hover:text-foreground" active-class="!text-foreground font-semibold">Главная</RouterLink>
          <RouterLink v-if="isAuthenticated()" to="/profile" class="text-muted-foreground transition-colors hover:text-foreground" active-class="!text-foreground font-semibold">Профиль</RouterLink>
        </nav>

        <div class="flex items-center gap-2">
          <template v-if="!isAuthenticated()">
            <Button variant="ghost" size="sm" @click="login">
              Войти
            </Button>
            <Button size="sm" @click="registration">
              Регистрация
            </Button>
          </template>

          <Button v-else variant="outline" size="sm" class="gap-1.5" @click="logout">
            <LogOut class="size-4" />
            <span class="hidden sm:inline">Выйти</span>
          </Button>
        </div>
      </div>
    </header>

    <main class="flex-1" :class="{ 'pb-28': duration }">
      <RouterView />
    </main>
    <aside v-if="duration"
        class="fixed bottom-6 inset-x-4 mx-auto max-w-2xl z-50 flex items-center gap-4 px-6 py-2.5 rounded-full border bg-card/90 backdrop-blur shadow-lg">
      <Button
          size="icon"
          class="size-10 rounded-full shrink-0 shadow-md"
          @click="playPauseAudio()"
      >
        <Pause v-if="isPlaying" class="size-4 fill-current" />
        <Play v-else class="size-4 fill-current ml-0.5" />
      </Button>
      <div class="flex items-center gap-1 text-xs font-mono text-muted-foreground shrink-0 select-none">
        <span>{{ formatTime(currentTime) }}</span>
        <span class="opacity-40">/</span>
        <span>{{ formatTime(duration) }}</span>
      </div>

      <Slider
          v-model="progressValue"
          :max="duration"
          :step="1"
          class="flex-1 cursor-pointer"
      />
    </aside>

  </div>
</template>
