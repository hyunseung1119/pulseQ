import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { useMutation } from '@tanstack/react-query'
import { login } from '@/features/auth/api'
import { useAuthStore } from '@/features/auth/store'
import { Button } from '@/shared/components/ui/Button'
import { Input } from '@/shared/components/ui/Input'
import { Zap } from 'lucide-react'

export function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const authLogin = useAuthStore((s) => s.login)
  const navigate = useNavigate()

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: (data) => {
      authLogin(data.accessToken)
      navigate({ to: '/dashboard' })
    },
  })

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    mutation.mutate({ email, password })
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="w-full max-w-sm space-y-6">
        <div className="text-center">
          <div className="mb-4 flex items-center justify-center gap-2">
            <Zap className="h-8 w-8 text-primary" />
            <span className="text-3xl font-bold text-foreground">PulseQ</span>
          </div>
          <p className="text-muted-foreground">Sign in to your dashboard</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="mb-1.5 block text-sm font-medium text-foreground">Email</label>
            <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@company.com" required />
          </div>
          <div>
            <label className="mb-1.5 block text-sm font-medium text-foreground">Password</label>
            <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Password" required />
          </div>

          {mutation.isError && (
            <p className="text-sm text-destructive">
              {(mutation.error as Error)?.message || 'Login failed'}
            </p>
          )}

          <Button type="submit" className="w-full" disabled={mutation.isPending}>
            {mutation.isPending ? 'Signing in...' : 'Sign In'}
          </Button>
        </form>

        <p className="text-center text-sm text-muted-foreground">
          Don't have an account?{' '}
          <Link to="/signup" className="font-medium text-primary hover:underline">
            Sign up
          </Link>
        </p>
      </div>
    </div>
  )
}
