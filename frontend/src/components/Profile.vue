<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import { authService } from '../composables/authService'
import { RouterLink, useRouter, useRoute } from "vue-router";
import { audioService } from '../composables/audioService'

import {Mic, Plus, History, AlertCircle, RotateCw, Clock, FileAudio, Play} from "@lucide/vue";
import {Card, CardContent, CardDescription, CardHeader, CardTitle} from "@/components/ui/card";
import {Avatar, AvatarImage, AvatarFallback} from "@/components/ui/avatar";
import {Tabs, TabsContent, TabsList, TabsTrigger} from "@/components/ui/tabs"
import {Badge} from "@/components/ui/badge";
import {Separator} from "@/components/ui/separator";
import {Button} from "@/components/ui/button";
import axios from "axios";
import {Alert, AlertDescription, AlertTitle} from "@/components/ui/alert";

const backendUrl = (import.meta.env.VITE_API_URL || 'http://localhost:8080')
const {playAudio} = audioService();
//.
interface Material {
  id: number
  title: string
  description: string | null
  type: string
  status: string
  authorName: string
  categoryName: string
  coverUrl: string | null
  audioUrl: string | null
  linksExpireAt: string | null
}

const router = useRouter()
const route = useRoute()
const { user } = authService()

const initials = computed(() => {
  if (!user.value.username) return '?'
  return user.value.username.slice(0, 2).toUpperCase()
})

const activeTab = ref((route.query.tab as string) || 'general')

watch(activeTab, (newTab) => {
  router.replace({ query: { ...route.query, tab: newTab } })
})

watch(() => route.query.tab, (newTab) => {
  if (typeof newTab === 'string') {
    activeTab.value = newTab
  }
})

//.
const materials = ref<Material[]>([])
const isLoading = ref(false)
const errorMessage = ref<string | null>(null)
const refreshingId = ref<number | null>(null)

//.
async function loadMaterials() {

  isLoading.value = true
  errorMessage.value = null

  try {
    const response = await axios.get<Material[]>(`${backendUrl}/rest-api/author/materials`, {
      withCredentials: true,
    })
    materials.value = response.data
  } catch (error: any) {
    errorMessage.value =
        error.response?.data?.detail ||
        error.response?.data?.message ||
        'Не удалось загрузить список материалов.'
  } finally {
    isLoading.value = false
  }
}

//.
async function refreshLinks(id: number) {
  refreshingId.value = id
  try {
    const response = await axios.get<Material>(
        `${backendUrl}/rest-api/author/materials/${id}`,
        { withCredentials: true }
    )
    const index = materials.value.findIndex((m) => m.id === id)
    if (index !== -1) {
      materials.value[index] = response.data
    }
  } catch (error) {
    console.error('Ошибка обновления ссылки:', error)
  } finally {
    refreshingId.value = null
  }
}

//.
function formatStatus(status: string) {
  switch (status) {
    case 'PUBLISHED':
      return { label: 'Опубликовано', variant: 'default' as const }
    case 'PENDING_MODERATION':
      return { label: 'На модерации', variant: 'secondary' as const }
    case 'PROCESSING':
    case 'UPLOADING':
      return { label: 'В обработке', variant: 'outline' as const }
    case 'REJECTED':
      return { label: 'Отклонено', variant: 'destructive' as const }
    case 'DRAFT':
      return { label: 'Черновик', variant: 'outline' as const }
    default:
      return { label: status, variant: 'outline' as const }
  }
}

//.
function formatExpiration(value: string) {
  return new Date(value).toLocaleTimeString('ru-RU', {
    hour: '2-digit',
    minute: '2-digit',
  })
}

function handleCardClick(item: Material) {
  if (!item.audioUrl) {
    console.warn('Аудиофайл еще не готов или ссылка отсутствует:', item.title)
    return
  }
  playAudio(item.audioUrl)
}

//.
onMounted(() =>{
  loadMaterials()
})
</script>

<template>
  <div class="w-full max-w-3xl mx-auto py-8 px-4 space-y-6">
    <Card>
      <CardHeader class="flex flex-row items-center gap-4 space-y-0">
        <Avatar class="size-16 border-2">
          <AvatarImage src="" :alt="user.username"/>
          <AvatarFallback class="text-lg font-bold"> {{ initials }} </AvatarFallback>
        </Avatar>
        <div class="space-y-1">
          <CardTitle class="text-xl">{{ user.username }}</CardTitle>

          <div class="flex flex-wrap gap-1.5 pt-1">
            <Badge v-for="role in user.roles"
                   :key="role"
                   variant="secondary">
              {{ role }}
            </Badge>
          </div>
        </div>
      </CardHeader>
    </Card>

    <Tabs v-model="activeTab" class="w-full">
      <TabsList class="grid w-full grid-cols-3">
        <TabsTrigger value="general">
          Общие данные
        </TabsTrigger>
        <TabsTrigger value="podcasts">
          Мои подкасты
        </TabsTrigger>
        <TabsTrigger value="history">
          История
        </TabsTrigger>
      </TabsList>

      <TabsContent value="general" class="mt-4">
        <Card>
          <CardHeader>
            <CardTitle class="text-lg">Информация об аккаунте</CardTitle>
          </CardHeader>
          <CardContent>
            <div class="flex justify-between items-center py-1">
              <span class="text-sm text-muted-foreground">Имя пользователя</span>
              <span class="text-sm font-medium">{{ user.username }}</span>
            </div>

            <Separator />

            <div class="flex justify-between items-center py-1">
              <span class="text-sm text-muted-foreground">Личные данные</span>
              <span class="text-sm font-medium">{{ user.lastName }} {{ user.firstName }}</span>
            </div>

            <Separator />

            <div class="flex justify-between items-center py-1">
              <span class="text-sm text-muted-foreground">Email</span>
              <span class="text-sm font-medium">{{ user.email }}</span>
            </div>
          </CardContent>
        </Card>
      </TabsContent>

      <TabsContent value="podcasts" class="mt-4 space-y-4">

        <Alert v-if="errorMessage" variant="destructive">
          <AlertCircle class="size-4" />
          <AlertTitle>Не удалось загрузить записи</AlertTitle>
          <AlertDescription class="flex justify-between items-center">
            <span>{{ errorMessage }}</span>
            <Button size="sm" variant="outline" @click="loadMaterials">Повторить</Button>
          </AlertDescription>
        </Alert>

        <div class="flex items-center justify-between">
          <h2 class="text-lg font-semibold tracking-tight">
            Загруженные записи ({{ materials.length }})
          </h2>
          <div class="flex gap-2">
            <Button
                variant="outline"
                size="sm"
                :disabled="isLoading"
                @click="loadMaterials"
            >
              <RotateCw class="size-3.5 mr-1.5" :class="{ 'animate-spin': isLoading }" />
              Обновить
            </Button>
            <Button size="sm" as-child>
              <RouterLink to="/upload">
                <Plus class="size-3.5 mr-1.5" />
                Загрузить
              </RouterLink>
            </Button>
          </div>
        </div>

        <Card v-if="!isLoading && materials.length === 0">
          <CardContent class="flex flex-col items-center justify-center text-center p-8 sm:p-12 space-y-4">
            <Mic class="size-12 text-muted-foreground stroke-1" />
            <div class="space-y-1">
              <CardTitle class="text-lg">Нет загруженных материалов</CardTitle>
              <CardDescription>
                Вы пока не опубликовали ни одного выпуска подкаста или аудиокниги.
              </CardDescription>
            </div>

            <Button as-child class="mt-2">
              <RouterLink to="/upload">
                <Plus class="mr-2 size-4"/>
                Загрузить материал
              </RouterLink>
            </Button>
          </CardContent>
        </Card>

        <!--...!-->
        <div v-else class="space-y-3">
          <Card
              v-for="item in materials"
              :key="item.id"
              class="overflow-hidden transition-all duration-200 group"
              :class="item.audioUrl ? 'cursor-pointer hover:border-primary/50 hover:bg-accent/30' : 'opacity-70'"
              @click="handleCardClick(item)"
          >
            <CardContent class="p-4 sm:p-5 flex gap-4 items-start">

              <div class="relative size-16 sm:size-20 rounded-md overflow-hidden bg-muted flex items-center justify-center shrink-0 border">
                <img
                    v-if="item.coverUrl"
                    :src="item.coverUrl"
                    :alt="item.title"
                    class="size-full object-cover transition-transform duration-300 group-hover:scale-105"
                />
                <FileAudio v-else class="size-7 text-muted-foreground" />

                <div
                    v-if="item.audioUrl"
                    class="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200"
                >
                  <Play class="size-6 text-white fill-white drop-shadow" />
                </div>
              </div>

              <div class="flex-1 min-w-0 space-y-1.5">
                <div class="flex flex-wrap items-center justify-between gap-2">
                  <h3 class="font-semibold text-base truncate group-hover:text-primary transition-colors">
                    {{ item.title }}
                  </h3>
                  <div class="flex items-center gap-1.5">
                    <Badge variant="outline">{{ item.categoryName }}</Badge>
                    <Badge :variant="formatStatus(item.status).variant">
                      {{ formatStatus(item.status).label }}
                    </Badge>
                  </div>
                </div>

                <p v-if="item.description" class="text-sm text-muted-foreground line-clamp-2">
                  {{ item.description }}
                </p>

                <div v-if="item.linksExpireAt" class="flex items-center justify-between text-xs text-muted-foreground pt-1">
                  <span class="flex items-center gap-1">
                    <Clock class="size-3" />
                    Ссылка активна до {{ formatExpiration(item.linksExpireAt) }}
                  </span>

                  <Button
                      variant="ghost"
                      size="sm"
                      class="h-6 px-2 text-xs relative z-10"
                      :disabled="refreshingId === item.id"
                      @click.stop="refreshLinks(item.id)"
                  >
                    <RotateCw
                        class="size-3 mr-1"
                        :class="{ 'animate-spin': refreshingId === item.id }"
                    />
                    {{ refreshingId === item.id ? '...' : 'Обновить' }}
                  </Button>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </TabsContent>

      <TabsContent value="history" class="mt-4">
        <Card>
          <CardContent class="flex flex-col items-center justify-center text-center p-8 sm:p-12 space-y-4">
            <History class="size-12 text-muted-foreground stroke-1" />

            <div class="space-y-1">
              <CardTitle class="text-lg">История прослушиваний пуста</CardTitle>
              <CardDescription>
                Здесь будут отображаться материалы, которые вы воспроизводили.
              </CardDescription>
            </div>
          </CardContent>
        </Card>
      </TabsContent>
    </Tabs>
  </div>
</template>
