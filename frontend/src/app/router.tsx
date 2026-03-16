import {
  createRouter,
  createRootRoute,
  createRoute,
  redirect,
  Outlet,
} from '@tanstack/react-router'
import { AUTH_TOKEN_KEY } from '@/shared/lib/constants'
import { LandingPage } from './routes/index'
import { LoginPage } from './routes/login'
import { SignupPage } from './routes/signup'
import { QueueWaitingPage } from './routes/queue.$eventSlug'
import { DashboardLayout } from './layout/DashboardLayout'
import { DashboardIndex } from './routes/dashboard/index'
import { EventsPage } from './routes/dashboard/events'
import { EventNewPage } from './routes/dashboard/events.new'
import { EventDetailPage } from './routes/dashboard/events.$id'
import { EventEditPage } from './routes/dashboard/events.$id.edit'
import { ApiKeysPage } from './routes/dashboard/api-keys'
import { UsagePage } from './routes/dashboard/usage'
import { SettingsPage } from './routes/dashboard/settings'

const rootRoute = createRootRoute({
  component: Outlet,
})

const landingRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: LandingPage,
})

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  component: LoginPage,
})

const signupRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/signup',
  component: SignupPage,
})

const queueRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/queue/$eventSlug',
  component: QueueWaitingPage,
})

const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard',
  component: DashboardLayout,
  beforeLoad: () => {
    const token = localStorage.getItem(AUTH_TOKEN_KEY)
    if (!token) {
      throw redirect({ to: '/login' })
    }
  },
})

const dashboardIndexRoute = createRoute({
  getParentRoute: () => dashboardRoute,
  path: '/',
  component: DashboardIndex,
})

const eventsRoute = createRoute({
  getParentRoute: () => dashboardRoute,
  path: '/events',
  component: EventsPage,
})

const eventNewRoute = createRoute({
  getParentRoute: () => dashboardRoute,
  path: '/events/new',
  component: EventNewPage,
})

const eventDetailRoute = createRoute({
  getParentRoute: () => dashboardRoute,
  path: '/events/$id',
  component: EventDetailPage,
})

const eventEditRoute = createRoute({
  getParentRoute: () => dashboardRoute,
  path: '/events/$id/edit',
  component: EventEditPage,
})

const apiKeysRoute = createRoute({
  getParentRoute: () => dashboardRoute,
  path: '/api-keys',
  component: ApiKeysPage,
})

const usageRoute = createRoute({
  getParentRoute: () => dashboardRoute,
  path: '/usage',
  component: UsagePage,
})

const settingsRoute = createRoute({
  getParentRoute: () => dashboardRoute,
  path: '/settings',
  component: SettingsPage,
})

const routeTree = rootRoute.addChildren([
  landingRoute,
  loginRoute,
  signupRoute,
  queueRoute,
  dashboardRoute.addChildren([
    dashboardIndexRoute,
    eventsRoute,
    eventNewRoute,
    eventDetailRoute,
    eventEditRoute,
    apiKeysRoute,
    usageRoute,
    settingsRoute,
  ]),
])

export const router = createRouter({ routeTree })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
