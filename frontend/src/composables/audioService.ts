import {ref} from "vue";
import { Howl } from "howler";
const isPlaying = ref(false);
const volume = ref(1.0)
const currentUrl = ref<string | null>(null);
let sound: Howl | null = null;

function playAudio(url: string) {
    if (currentUrl.value !== url) {
        sound?.stop()
        sound = new Howl({
            src: [url],
            volume: volume.value,
            html5: true,
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
        playAudio,
        pauseAudio,
    }
}