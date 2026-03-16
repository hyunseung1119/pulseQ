import { create } from 'zustand'
import { AUTH_TOKEN_KEY } from '@/shared/lib/constants'

interface AuthState {
  token: string | null
  isAuthenticated: boolean
  login: (token: string) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  token: localStorage.getItem(AUTH_TOKEN_KEY),
  isAuthenticated: !!localStorage.getItem(AUTH_TOKEN_KEY),
  login: (token: string) => {
    localStorage.setItem(AUTH_TOKEN_KEY, token)
    set({ token, isAuthenticated: true })
  },
  logout: () => {
    localStorage.removeItem(AUTH_TOKEN_KEY)
    set({ token: null, isAuthenticated: false })
  },
}))
