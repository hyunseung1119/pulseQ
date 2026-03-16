import { Link, Outlet, useNavigate, useMatchRoute } from '@tanstack/react-router'
import { useAuthStore } from '@/features/auth/store'
import { LayoutDashboard, Calendar, Key, BarChart3, Settings, LogOut, Zap } from 'lucide-react'
import { cn } from '@/shared/lib/cn'

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, exact: true },
  { to: '/dashboard/events', label: 'Events', icon: Calendar, exact: false },
  { to: '/dashboard/api-keys', label: 'API Keys', icon: Key, exact: true },
  { to: '/dashboard/usage', label: 'Usage', icon: BarChart3, exact: true },
  { to: '/dashboard/settings', label: 'Settings', icon: Settings, exact: true },
] as const

export function DashboardLayout() {
  const logout = useAuthStore((s) => s.logout)
  const navigate = useNavigate()
  const matchRoute = useMatchRoute()

  const handleLogout = () => {
    logout()
    navigate({ to: '/login' })
  }

  return (
    <div className="flex h-screen bg-background">
      <aside className="flex w-64 flex-col border-r border-border bg-card">
        <div className="flex h-16 items-center gap-2 border-b border-border px-6">
          <Zap className="h-6 w-6 text-primary" />
          <span className="text-xl font-bold text-foreground">PulseQ</span>
        </div>

        <nav className="flex-1 space-y-1 px-3 py-4">
          {navItems.map(({ to, label, icon: Icon, exact }) => {
            const isActive = exact ? matchRoute({ to }) : matchRoute({ to, fuzzy: true })
            return (
              <Link
                key={to}
                to={to}
                className={cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  isActive
                    ? 'bg-primary/10 text-primary'
                    : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
                )}
              >
                <Icon className="h-4 w-4" />
                {label}
              </Link>
            )
          })}
        </nav>

        <div className="border-t border-border p-3">
          <button
            onClick={handleLogout}
            className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-accent-foreground"
          >
            <LogOut className="h-4 w-4" />
            Logout
          </button>
        </div>
      </aside>

      <main className="flex-1 overflow-auto">
        <div className="mx-auto max-w-7xl p-6">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
