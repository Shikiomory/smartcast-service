import {ref} from "vue";

export interface UserState {
    isAuthenticated: boolean;
    id: number | null;
    username: string;
    email: string;
    firstName: string;
    lastName: string;
    roles: string[];
}

interface AuthResponse {
    id: number;
    username: string;
    email: string;
    firstName: string;
    lastName: string;
    roles: string[];
}

const backendUrl = import.meta.env.VITE_API_URL || "http://localhost:8080";
const user = ref<UserState>({
    isAuthenticated: false,
    id: null,
    username: "",
    email: "",
    firstName: "",
    lastName: "",
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
                id: data.id,
                username: data.username,
                email: data.email,
                firstName: data.firstName,
                lastName: data.lastName,
                roles: data.roles,
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
    window.location.href = backendUrl + "/rest-api/auth/register";
}

async function login() {
    window.location.href = backendUrl + "/rest-api/auth/login";
}

async function logout() {
    resetAuth()
    window.location.href = backendUrl + "/rest-api/auth/logout";
}

function resetAuth() {
    user.value = {
        isAuthenticated: false,
        id: null,
        username: "",
        email: "",
        firstName: "",
        lastName: "",
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