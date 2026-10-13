import {ref} from "vue";

export interface UserState {
    isAuthenticated: boolean;
    username: string;
    roles: string[];
}

interface AuthResponse {
    username: string;
    roles: string[];
}

const backendUrl = import.meta.env.VITE_API_URL;
export const user = ref<UserState>( {
    isAuthenticated: false,
    username: "",
    roles: []
})

export async function authStatus() {
    try {
        const response = await fetch(backendUrl + "/rest-api/public/auth/me", {
            credentials: "include",
        });

        if (response.ok) {
            const data: AuthResponse = await response.json();
            user.value = {
                isAuthenticated: true,
                username: data.username,
                roles: data.roles
            };
        }
    } catch (error) {
        console.log("Ошибка проверки авторизации: " + error);
        resetAuth()
    }
}

export async function registration() {
    window.location.href = backendUrl + "/rest-api/public/auth/register";
}

export async function login() {
    window.location.href = backendUrl + "/rest-api/public/auth/login";
}

export async function logout() {
    try {
        const response = await fetch(backendUrl + "/rest-api/public/auth/logout");

        if (response.ok) {
            console.log("Пользователь " + user.value.username + " успешно вышел")
            resetAuth()
        }
    } catch (error) {
        console.log("Ошибка выхода: " + error);
    }
}
function resetAuth() {
    user.value = {
        isAuthenticated: false,
        username: "",
        roles: []
    }
}