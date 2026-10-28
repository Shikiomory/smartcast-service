<script setup lang="ts">

import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import {
  Field,
  FieldDescription,
  FieldLabel,
  FieldGroup,
  FieldSeparator,
} from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import {
  Attachment,
  AttachmentAction,
  AttachmentActions,
  AttachmentContent,
  AttachmentDescription,
  AttachmentMedia,
  AttachmentTitle,
} from '@/components/ui/attachment'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  TagsInput,
  TagsInputInput,
  TagsInputItem,
  TagsInputItemDelete,
  TagsInputItemText,
} from '@/components/ui/tags-input'
import { Textarea } from '@/components/ui/textarea'
import {Progress} from "@/components/ui/progress";

import { FileCodeIcon, XIcon, Loader2, CheckCircle, AlertCircle, ArrowLeftIcon} from '@lucide/vue'
import {computed, onMounted, onUnmounted, ref} from "vue"
import axios from 'axios'
import {Alert, AlertDescription, AlertTitle} from "@/components/ui/alert";

import { useRouter, onBeforeRouteLeave} from "vue-router";
const router = useRouter()

interface metaData {
  title: string
  author: string
  type: string
  categoryId: string
  tags: string[]
  description: string
}

interface Category {
  id: number
  name: string
  description: string | null
}

const soundFile = ref()
const coverFile = ref()
const coverPreviewUrl = ref()
const categories = ref<Category[]>([])

const form = ref<metaData>({
  title: '',
  author: '',
  type: 'PODCAST',
  categoryId: '',
  tags: [],
  description: '',
})

const backendUrl = import.meta.env.VITE_API_URL || "http://localhost:8080";
const uploadProgress = ref(0)
const isSubmitting = ref(false)
const errorMessage = ref<string | null>(null)
const successMessage = ref<string | null>(null)

let abortController: AbortController |null = null

const isDirty = computed(() => {
  return Boolean(
      soundFile.value || coverFile.value || form.value.title.trim() || form.value.categoryId.trim() || form.value.author.trim() || form.value.description.trim()
      || form.value.tags.length > 0
  )
})

async function loadCategories() {
  try {
    const response = await axios.get<Category[]>(backendUrl + "/rest-api/public/categories", {
      withCredentials: true,
    })
      categories.value = response.data
  } catch(error) {
    console.log("Ошибка загрузки категорий: " + error)
  }
}

function formatFileSize(bytes: number) {
  if (!bytes) return ''
  const mb = (bytes / (1024 * 1024)).toFixed(2)
  return `${mb} MB`
}

function uploadFile(event: Event) {
  const target = event.target as HTMLInputElement;

  if (target.files && target.files[0]) {
    soundFile.value = target.files[0]
    console.log("Файл загружен с диска:" + soundFile.value.name)
  }

}

function uploadCover(event: Event) {
  const target = event.target as HTMLInputElement;

  if (target.files && target.files[0]) {
    const file = target.files[0]
    coverFile.value =file
    console.log("Файл загружен с диска:" + coverFile.value.name)
    coverPreviewUrl.value = URL.createObjectURL(file)
  }

}


async function uploadAudio() {
  try {
    if (!soundFile.value || !form.value.title.trim() || !form.value.categoryId) {
      errorMessage.value = "Заполните название, выберите категорию и загрузите файл"
      return;
    }

    isSubmitting.value = true
    uploadProgress.value = 0
    errorMessage.value = null
    successMessage.value = null
    abortController = new AbortController()

    const formData = new FormData();

    formData.append("file", soundFile.value);
    if (coverFile.value) {
      formData.append("cover", coverFile.value);
    }

    formData.append("title", form.value.title)
    formData.append("author", form.value.author)
    formData.append("type", form.value.type)
    formData.append("categoryId", form.value.categoryId)
    formData.append("description", form.value.description)
    form.value.tags.forEach(tag => {
      formData.append("tags", tag)
    })
    await axios.post(backendUrl + "/rest-api/author/new-material", formData, {
      withCredentials: true,
      signal: abortController?.signal,
      onUploadProgress: (progress) => {
        if (progress.total) {
          uploadProgress.value = Math.round(progress.loaded * 100 / progress.total)
        }
      }
    })

    successMessage.value= 'Материал успешно загружен и отправлен на модерацию'
    clearForm()
  } catch (error) {
    let message = 'Ошибка 0, нет ошибок'

    if (axios.isAxiosError(error)) {
      if (axios.isCancel(error)) {
        console.log("Пользователь отменил загрузку")
        return
      }
      else if (error.response?.status === 403) {
        message = "Доступ запрещен, загружать материал может только пользователь с ролью Автор"
      }
      else if (error.response?.status === 413) {
        message = "Файл слишком большой."
      }
      else {
        message = error.response?.data.detail || error.response?.data.message || error.message || 'Ошибка сети'
      }
    } else if (error instanceof Error) {
      message = error.message
    }
    errorMessage.value = message
  } finally {
    isSubmitting.value = false
    uploadProgress.value = 0
  }
}

function clearForm() {
  form.value = {
    title: '',
    author: '',
    type: 'PODCAST',
    categoryId: '',
    tags: [],
    description: '',
  }
  removeCover()
  removeAudio()
  soundFile.value = null;
}

function removeAudio() {
  soundFile.value = null
}

function removeCover() {
  coverFile.value = null;
  if (coverPreviewUrl.value) {
    URL.revokeObjectURL(coverPreviewUrl.value)
    coverPreviewUrl.value = null
  }
}

function cancelUpload() {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
}

function handleBeforeUnload(e: BeforeUnloadEvent) {
  if (isSubmitting.value || isDirty.value) {
    e.preventDefault()
  }
}

onBeforeRouteLeave(() => {
  if (isSubmitting.value || isDirty.value) {
    const confirmLeave = window.confirm("Если вы уйдете, несохраненные данные будут утеряны. Продолжить?")
    if (!confirmLeave) {
      return false
    }

    cancelUpload()
  }
})
onMounted(() => {
  loadCategories()
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
  cancelUpload()
})

</script>

<template>
    <Card class="w-full max-w-3xl mx-auto my-10">
      <CardHeader>
        <Button variant="ghost" class="w-fit cursor-pointer" size="sm" aria-label="назад"
                @click="router.push({ path: '/profile', query: { tab: 'podcasts' } })">
          <ArrowLeftIcon class="size-4"></ArrowLeftIcon>
          назад
        </Button>
        <CardTitle>
          Загрузка аудиозаписи
        </CardTitle>
        <CardDescription>
          Заполните метаданные и прикрепите аудиофайл для публикации.
        </CardDescription>
      </CardHeader>

      <CardContent class="space-y-6">
        <Field>
          <FieldLabel>
            Аудиофайл
          </FieldLabel>

          <Input v-if="!soundFile" type="file" @change="uploadFile" />

          <Attachment v-else>
            <AttachmentMedia>
              <FileCodeIcon class="size-4"/>
            </AttachmentMedia>
            <AttachmentContent>
              <AttachmentTitle>
                {{ soundFile.name }}
              </AttachmentTitle>
              <AttachmentDescription>
                {{ formatFileSize(soundFile.size) }}
              </AttachmentDescription>
            </AttachmentContent>
            <AttachmentActions>
              <AttachmentAction aria-label="Удалить файл" @click="removeAudio()">
                <XIcon class="size-4"/>
              </AttachmentAction>
            </AttachmentActions>
          </Attachment>
          <FieldDescription>
            Выберите аудиозапись в формате MP3, WAV или FLAC
          </FieldDescription>
        </Field>

        <Field>
          <FieldLabel>Обложка</FieldLabel>
          <Input v-if="!coverFile" type="file" @change="uploadCover" />
          <Attachment orientation="vertical" v-else>
            <AttachmentMedia variant="image">
              <img :src="coverPreviewUrl" alt="Обложка">
            </AttachmentMedia>
            <AttachmentContent>
              <AttachmentTitle>
                {{ coverFile.name }}
              </AttachmentTitle>
              <AttachmentDescription>
                {{ formatFileSize(coverFile.size) }}
              </AttachmentDescription>
            </AttachmentContent>
            <AttachmentActions>
              <AttachmentAction aria-label="Удалить обложку" @click="removeCover()">
                <XIcon class="size-4"/>
              </AttachmentAction>
            </AttachmentActions>
          </Attachment>
          <FieldDescription>Выберите изображение в формате PNG, JPG или WebP</FieldDescription>
        </Field>
        <FieldSeparator />

        <FieldGroup class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <Field>
            <FieldLabel>Название аудиофайла</FieldLabel>
            <Input v-model="form.title" placeholder="Название аудиофайла"/>
          </Field>
          <Field>
            <FieldLabel>Автор</FieldLabel>
            <Input v-model="form.author" placeholder="автор" />
          </Field>
          <Field>
            <FieldLabel>Тип аудио</FieldLabel>
            <Select v-model="form.type">
              <SelectTrigger>
                <SelectValue placeholder="Выберите тип"></SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="PODCAST">подкаст</SelectItem>
                <SelectItem value="AUDIOBOOK">аудиокнига</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <Field>
            <FieldLabel>Категория</FieldLabel>
            <Select v-model="form.categoryId">
              <SelectTrigger>
                <SelectValue placeholder="Категория"></SelectValue>
              </SelectTrigger>
              <SelectContent>
                <SelectItem v-for="category in categories" :key="category.id" :value="String(category.id)"> {{ category.name }}</SelectItem>
              </SelectContent>
            </Select>
          </Field>
          <Field class="md:col-span-2">
            <FieldLabel>Ключевые слова и теги</FieldLabel>
            <TagsInput v-model="form.tags">
              <TagsInputItem v-for="item in form.tags" :key="item" :value="item">
                <TagsInputItemText />
                <TagsInputItemDelete />
              </TagsInputItem>
              <TagsInputInput placeholder="Введите тег и нажмите Enter"/>
            </TagsInput>
            <FieldDescription>Для лучшей характеристики вашего материала и удобства поиска слушателями</FieldDescription>
          </Field>
          <Field class="md:col-span-2">
            <FieldLabel>Описание</FieldLabel>
            <Textarea
                v-model="form.description"
                placeholder="Описание"
            />
            <FieldDescription>Подробная информация, которая будет отображаться в карточке</FieldDescription>
          </Field>
        </FieldGroup>



      </CardContent>
      <CardFooter class="flex flex-col gap-4">
        <FieldGroup class="flex flex-col gap-4 sm:flex-row sm:items-center w-full">
          <Field v-if="isSubmitting" class="w-full sm:max-w-xs">
            <FieldLabel class="flex justify-between text-xs">
              Отправка данных...
              <FieldDescription as="span">{{ uploadProgress }}%</FieldDescription>
            </FieldLabel>
            <Progress :model-value="uploadProgress" class="h-2"/>
          </Field>

          <Button class="w-full sm:w-auto ml-auto" @click="uploadAudio" :disabled="!soundFile || isSubmitting">
            <Loader2 v-if="isSubmitting" class="mr-2 size-4 animate-spin"/>
            {{ isSubmitting ? 'Загрузка' : 'Загрузить' }}
          </Button>
        </FieldGroup>

        <Alert v-if="errorMessage" variant="destructive" class="w-full">
          <AlertCircle class="size-4" />
          <AlertTitle>Ошибка при отправке</AlertTitle>
          <AlertDescription>
            {{errorMessage}}
          </AlertDescription>
        </Alert>

        <Alert v-if="successMessage" class="w-full">
          <CheckCircle class="size-4" />
          <AlertTitle>Успешно</AlertTitle>
          <AlertDescription> {{successMessage}}</AlertDescription>
        </Alert>
      </CardFooter>
    </Card>
</template>