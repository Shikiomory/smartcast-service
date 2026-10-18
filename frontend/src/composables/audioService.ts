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

function seekAudio(value: number) {
    sound?.seek(value)
    currentTime.value = value
}

function formatTime(value: number | null) {
    if (value || value === 0) {
        const mins = Math.floor(value / 60);
        const secs = Math.floor(value % 60);
        const formattedSecs = secs < 10 ? `0${secs}` : secs;
        return `${mins}:${formattedSecs}`;
    }
    return "-:-"
}

export function audioService() {
    return {
        isPlaying,
        volume,
        duration,
        currentTime,
        formatTime,
        playAudio,
        pauseAudio,
        seekAudio,
    }
}