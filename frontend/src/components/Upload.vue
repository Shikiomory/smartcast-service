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
import { FileCodeIcon, XIcon } from '@lucide/vue'
import {ref} from "vue";


const soundFile = ref()
const coverFile = ref()
const coverPreviewUrl = ref()

  interface metaData {
    title: string
    author: string
    type: string
    categoryId: string
    tags: string[]
    description: string
  }

  const form = ref<metaData>({
    title: '',
    author: '',
    type: '',
    categoryId: '',
    tags: [],
    description: '',
  })

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

//TODO: потом объединить
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
    if (!soundFile.value) {
      alert("Файл не выбран")
      return;
    }

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
    <Card class="w-full max-w-3xl mx-auto my-10">
      <CardHeader>
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
              <AttachmentAction aria-label="Удалить файл" @click="soundFile = null">
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
              <AttachmentAction aria-label="Удалить обложку" @click="coverFile = null; coverPreviewUrl.value = null">
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
                <SelectItem value="1">Технологии и IT</SelectItem>
                <SelectItem value="2">Бизнес и финансы</SelectItem>
                <SelectItem value="3">Художественная литература</SelectItem>
                <SelectItem value="4">История</SelectItem>
                <SelectItem value="5">Пивоварение</SelectItem>
                <SelectItem value="6">Наука и образование</SelectItem>
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

      <CardFooter class="flex justify-end">
        <Button @click="uploadAudio" :disabled="!soundFile || form.title === ''"> Загрузить </Button>
      </CardFooter>
    </Card>
</template>