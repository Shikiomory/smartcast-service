import {createRouter, createWebHistory, type RouteRecordRaw} from "vue-router"
import {authService} from "../composables/authService";
const {authStatus, user} = authService()

import HomeView from "../components/HomeView.vue";
import Profile from "../components/Profile.vue";
import ModeratorView from "../components/ModeratorView.vue";


const routes: RouteRecordRaw[] = [
    {
        path: '/',
        name: 'home',
        component: HomeView,
    },
    {
        path: '/profile',
        name: 'profile',
        component: Profile,
        meta: {
            requiresAuth: true,
        }
    },
    {
        path: '/moderation',
        name: 'moderation',
        component: ModeratorView,
        meta: {requiresAuth: true, requiresModeration: true},
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach(async (to) => {
    const requiresAuth = to.meta.requiresAuth;
    if (requiresAuth) {
        await authStatus();
        if (!user.value.isAuthenticated) {
            return {name: 'home'}
        }
    }
    if (to.meta.requiresModeration && !user.value.roles.some(role => role === 'MODERATOR' || role === 'ADMIN')) {
        return {name: 'home'}
    }

})
export default router
