import { createApp } from 'vue'
import App from './App.vue'

import api from './api'

import {createRouter, createWebHistory} from 'vue-router'

import initVue from './prime_vue'

const app = createApp(App)
initVue(app)

import './main.css'

api.init(app);


import routes from './routes'

const router = createRouter({
    history: createWebHistory(),
    routes
})
app.use(router);

app.mount('#app')


