import axios from 'axios'
import { AUTH_TOKEN_KEY } from './constants'

const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(AUTH_TOKEN_KEY)
      window.location.href = '/login'
    }
    const serverMessage = error.response?.data?.message ?? error.response?.data?.detail
    return Promise.reject(new Error(serverMessage ?? error.message))
  },
)

export default apiClient
