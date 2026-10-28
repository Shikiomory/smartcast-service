import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import ts from 'typescript'

const source = readFileSync(new URL('../src/composables/audioService.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022},
}).outputText

function deferred() {
  let resolve
  let reject
  const promise = new Promise((done, fail) => {resolve = done; reject = fail})
  return {promise, resolve, reject}
}

async function settle() {
  // Flush the request continuation and asynchronous media callbacks.
  for (let index = 0; index < 8; index++) await Promise.resolve()
}

function createPlayer() {
  const howls = []
  const requests = []
  const responses = []
  const timers = new Map()
  let timerId = 0
  let now = Date.parse('2026-10-27T12:00:00Z')

  class FakeAudio {
    pauseCalls = 0
    pause() {this.pauseCalls++}
  }

  class FakeHowl {
    position = 0
    length = 90
    playCalls = 0
    pauseCalls = 0
    unloadCalls = 0
    active = false
    ended = false

    constructor(options) {
      this.options = options
      howls.push(this)
      queueMicrotask(() => options.onload?.())
    }

    play() {
      this.playCalls++
      if (this.ended) this.position = 0
      this.ended = false
      this.active = true
      queueMicrotask(() => this.options.onplay?.())
    }

    pause() {
      this.pauseCalls++
      this.active = false
      queueMicrotask(() => this.options.onpause?.())
    }

    unload() {
      this.unloadCalls++
      this.active = false
    }

    playing() {return this.active}
    duration() {return this.length}
    seek(value) {
      if (value !== undefined) this.position = value
      return this.position
    }

    end() {
      this.position = this.length
      this.active = false
      this.ended = true
      queueMicrotask(() => this.options.onend?.())
    }

    fail() {
      this.active = false
      queueMicrotask(() => this.options.onloaderror?.())
    }
  }

  const makeMaterial = (id, signature = requests.length) => ({
    id,
    title: `Материал ${id}`,
    audioUrl: `https://storage.test/audio/${id}.wav?signature=${signature}`,
    audioContentType: 'audio/wav',
    linksExpireAt: new Date(now + 60_000).toISOString(),
  })
  const module = {exports: {}}
  const context = vm.createContext({
    module,
    exports: module.exports,
    require(name) {
      if (name === 'vue') return {ref: value => ({value})}
      if (name === 'howler') return {Howl: FakeHowl}
      if (name === './api') return {
        request(path) {
          requests.push(path)
          if (responses.length) return responses.shift()
          const id = Number(path.split('/').at(-1))
          return Promise.resolve(makeMaterial(id))
        },
      }
      throw new Error(`Unexpected import: ${name}`)
    },
    Date: class extends Date {static now() {return now}},
    HTMLAudioElement: FakeAudio,
    setInterval(callback) {timers.set(++timerId, callback); return timerId},
    clearInterval(id) {timers.delete(id)},
  })
  vm.runInContext(compiled, context, {filename: 'audioService.js'})
  return {
    player: module.exports.audioService(), howls, requests,
    makeMaterial,
    queueResponse(response) {responses.push(response)},
    advance(milliseconds) {now += milliseconds},
    preview() {return new FakeAudio()},
  }
}

test('switching cards unloads the playing recording and starts the selected one', async () => {
  const {player, howls} = createPlayer()
  await player.playMaterial(1, 'Первый')
  await settle()
  assert.equal(player.isPlaying.value, true)

  await player.playMaterial(2, 'Второй')
  await settle()
  assert.equal(howls[0].unloadCalls, 1)
  assert.equal(howls[0].active, false)
  assert.equal(howls[1].playCalls, 1)
  assert.match(howls[1].options.src[0], /\/2\.wav/)
  assert.equal(player.currentMaterialId.value, 2)
  assert.equal(player.isPlaying.value, true)
})

test('pause and resume keep the selected URL and playback position', async () => {
  const {player, howls, requests} = createPlayer()
  await player.playMaterial(1, 'Первый')
  await settle()
  player.seekAudio(23)
  player.pauseAudio()
  await settle()
  assert.equal(player.isPlaying.value, false)

  player.resumeAudio()
  await settle()
  assert.equal(requests.length, 1)
  assert.equal(howls.length, 1)
  assert.equal(howls[0].position, 23)
  assert.equal(player.isPlaying.value, true)
})

test('a late response for the previous card cannot replace the newer selection', async () => {
  const {player, howls, queueResponse, makeMaterial} = createPlayer()
  const first = deferred()
  const second = deferred()
  queueResponse(first.promise)
  queueResponse(second.promise)
  const firstPlay = player.playMaterial(1, 'Первый')
  const secondPlay = player.playMaterial(2, 'Второй')
  second.resolve(makeMaterial(2))
  await secondPlay
  await settle()
  first.resolve(makeMaterial(1))
  await firstPlay
  await settle()

  assert.equal(player.currentMaterialId.value, 2)
  assert.equal(player.currentTitle.value, 'Материал 2')
  assert.equal(player.loading.value, false)
  assert.equal(howls.length, 1)
  assert.match(howls[0].options.src[0], /\/2\.wav/)
})

test('playing the finished recording with a fresh signed URL starts from zero', async () => {
  const {player, howls} = createPlayer()
  await player.playMaterial(1, 'Первый')
  await settle()
  howls[0].end()
  await settle()
  assert.equal(player.currentTime.value, 90)

  await player.playMaterial(1, 'Первый')
  await settle()
  assert.equal(howls.length, 2)
  assert.equal(howls[1].position, 0)
  assert.equal(player.currentTime.value, 0)
  assert.equal(player.isPlaying.value, true)
})

test('a media load failure permits retry with a fresh URL', async () => {
  const {player, howls, requests} = createPlayer()
  await player.playMaterial(1, 'Первый')
  await settle()
  howls[0].fail()
  await settle()
  assert.ok(player.errorMessage.value)
  assert.equal(player.isPlaying.value, false)

  player.resumeAudio()
  await settle()
  assert.equal(requests.length, 2)
  assert.equal(howls.length, 2)
  assert.equal(howls[1].playCalls, 1)
  assert.equal(player.isPlaying.value, true)
  assert.equal(player.errorMessage.value, '')
})

test('an API failure leaves the selected recording available for retry', async () => {
  const {player, queueResponse, requests, howls} = createPlayer()
  queueResponse(Promise.reject(new Error('Сеть недоступна')))
  await player.playMaterial(1, 'Первый')
  assert.ok(player.errorMessage.value)
  assert.equal(player.loading.value, false)

  player.resumeAudio()
  await settle()
  assert.equal(requests.length, 2)
  assert.equal(howls.length, 1)
  assert.equal(player.isPlaying.value, true)
})

test('resuming an expired signed URL refreshes it and preserves the position', async () => {
  const {player, howls, requests, advance} = createPlayer()
  await player.playMaterial(1, 'Первый')
  await settle()
  player.seekAudio(17)
  player.pauseAudio()
  await settle()
  advance(61_000)
  player.resumeAudio()
  await settle()

  assert.equal(requests.length, 2)
  assert.equal(howls.length, 2)
  assert.notEqual(howls[1].options.src[0], howls[0].options.src[0])
  assert.equal(howls[1].position, 17)
  assert.equal(player.currentTime.value, 17)
  assert.equal(player.isPlaying.value, true)
})

test('native previews exclude the global player and each other', async () => {
  const {player, preview} = createPlayer()
  await player.playMaterial(1, 'Первый')
  await settle()
  const first = preview()
  const second = preview()
  player.playPreview({currentTarget: first})
  await settle()
  assert.equal(player.isPlaying.value, false)
  player.playPreview({currentTarget: second})
  assert.equal(first.pauseCalls, 1)

  player.resumeAudio()
  await settle()
  assert.equal(second.pauseCalls, 1)
  assert.equal(player.isPlaying.value, true)

  player.playPreview({currentTarget: first})
  await player.playMaterial(2, 'Второй')
  await settle()
  assert.equal(first.pauseCalls, 2)
  assert.equal(player.currentMaterialId.value, 2)
  assert.equal(player.isPlaying.value, true)
})

test('pausing while metadata loads prevents unexpected autoplay on its arrival', async () => {
  const {player, howls, queueResponse, makeMaterial} = createPlayer()
  const response = deferred()
  queueResponse(response.promise)
  const playing = player.playMaterial(1, 'Первый')
  player.pauseAudio()
  response.resolve(makeMaterial(1))
  await playing
  await settle()

  assert.equal(howls.length, 0)
  assert.equal(player.isPlaying.value, false)
  assert.equal(player.loading.value, false)
})
