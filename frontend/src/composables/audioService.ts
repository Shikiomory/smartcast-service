import {ref} from "vue";
import { Howl } from "howler";
const isPlaying = ref(false);
const volume = ref(1.0)
const currentUrl = ref<string | null>(null);
const duration = ref<number | null>(null);
const currentTime = ref<number | null>(null);

let sound: Howl | null = null;
let timer: number | null = null;

function startTimer() {
    if (!timer) {
        timer = setInterval(getCurrentTime, 100)
    }
}

function stopTimer() {
    if (timer) {
        clearInterval(timer);
    }
    timer = null;
}

function getCurrentTime() {
    currentTime.value = sound?.seek() ?? null;
}
function playAudio(url: string) {
    if (currentUrl.value !== url) {
        sound?.stop()
        sound = new Howl({
            src: [url],
            volume: volume.value,
            html5: true,
            onload: () => {duration.value = sound?.duration() ?? null},
            onplay: () => {startTimer() },
            onpause: () => {stopTimer() },
            onend: () => { isPlaying.value = false },
        });
    }

    if (sound && !isPlaying.value) {
        sound.play();
    }

    currentUrl.value = url;
    isPlaying.value = true;
}

function pauseAudio() {
    if (isPlaying.value) {
        sound?.pause();
        isPlaying.value = false;
    }
}

export function audioService() {
    return {
        isPlaying,
        volume,
        duration,
        currentTime,
        playAudio,
        pauseAudio,
    }
}