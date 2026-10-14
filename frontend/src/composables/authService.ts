import {ref} from "vue";

export interface UserState {
    isAuthenticated: boolean;
    username: string;
    roles: string[];
}

interface AuthResponse {
    username: string;
    email: string;
    firstName: string;
    lastName: string;
}

const backendUrl = import.meta.env.VITE_API_URL || "http://localhost:8080";
const user = ref<UserState>({
    isAuthenticated: false,
    username: "",
    roles: []
})

async function authStatus() {
    try {
        const response = await fetch(backendUrl + "/rest-api/auth/status", {
            credentials: "include",
        });

        if (response.ok) {
            const data: AuthResponse = await response.json();
            user.value = {
                isAuthenticated: true,
                username: data.username,
                roles: []
            };
        } else {
            resetAuth()
        }
    } catch (error) {
        console.log("Ошибка проверки авторизации: " + error);
        resetAuth()
    }
}

function isAuthenticated() {
    return user.value.isAuthenticated
}

async function registration() {
    window.location.href = backendUrl + "/rest-api/auth/login";
}

async function login() {
    window.location.href = backendUrl + "/rest-api/auth/login";
}

async function logout() {
    try {
        const response = await fetch(backendUrl + "/rest-api/auth/logout", {
            method: "POST",
            credentials: "include",
        });

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

export function authService() {
    return {
        user,
        isAuthenticated,
        authStatus,
        login,
        registration,
        logout,
    }
}