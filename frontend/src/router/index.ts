import {createRouter, createWebHistory, type RouteRecordRaw} from "vue-router"
import {authService} from "../composables/authService";
const {authStatus, user} = authService()

import HomeView from "../components/HomeView.vue";
import Profile from "../components/Profile.vue";
import Upload from "../components/Upload.vue";


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
        path: '/upload',
        name: 'upload',
        component: Upload,
        meta: {
            requiresAuth: true,
        }
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach(async (to) => {
    const requiresAuth = to.meta.requiresAuth;
    if (requiresAuth && !user.value.isAuthenticated) {
        await authStatus();
        if (!user.value.isAuthenticated) {
            return {name: 'home'}
        }
    }

})
export default router