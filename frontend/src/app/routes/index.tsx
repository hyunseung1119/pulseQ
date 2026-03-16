import { Link } from '@tanstack/react-router'
import { Button } from '@/shared/components/ui/Button'
import { Zap, Shield, BarChart3, Clock } from 'lucide-react'

const features = [
  { icon: Clock, title: 'Real-time Queue', desc: 'Redis Sorted Set + WebSocket for sub-second updates' },
  { icon: Shield, title: 'Bot Detection', desc: 'LightGBM ML model with rule-based fallback' },
  { icon: BarChart3, title: 'Live Analytics', desc: 'Kafka event pipeline with real-time stats' },
  { icon: Zap, title: 'High Performance', desc: 'Non-blocking reactive architecture (WebFlux)' },
]

export function LandingPage() {
  return (
    <div className="flex min-h-screen flex-col bg-background">
      <header className="flex items-center justify-between border-b border-border px-8 py-4">
        <div className="flex items-center gap-2">
          <Zap className="h-6 w-6 text-primary" />
          <span className="text-xl font-bold text-foreground">PulseQ</span>
        </div>
        <div className="flex items-center gap-3">
          <Link to="/login"><Button variant="ghost">Sign In</Button></Link>
          <Link to="/signup"><Button>Get Started</Button></Link>
        </div>
      </header>

      <main className="flex flex-1 flex-col items-center justify-center px-8 text-center">
        <h1 className="mb-4 text-5xl font-bold tracking-tight text-foreground">
          Fair Queue Distribution
          <br />
          <span className="text-primary">at Scale</span>
        </h1>
        <p className="mb-8 max-w-xl text-lg text-muted-foreground">
          Real-time queue engine for high-traffic events. Ticket sales, course registration,
          flash deals — handle millions with fairness guaranteed.
        </p>
        <div className="flex gap-4">
          <Link to="/signup"><Button size="lg">Start Free</Button></Link>
          <a href="https://github.com/hyunseung1119/pulseQ" target="_blank" rel="noopener noreferrer">
            <Button variant="outline" size="lg">GitHub</Button>
          </a>
        </div>

        <div className="mt-20 grid max-w-4xl gap-8 sm:grid-cols-2 lg:grid-cols-4">
          {features.map(({ icon: Icon, title, desc }) => (
            <div key={title} className="rounded-lg border border-border bg-card p-6 text-left">
              <Icon className="mb-3 h-8 w-8 text-primary" />
              <h3 className="mb-1 font-semibold text-card-foreground">{title}</h3>
              <p className="text-sm text-muted-foreground">{desc}</p>
            </div>
          ))}
        </div>
      </main>

      <footer className="border-t border-border px-8 py-6 text-center text-sm text-muted-foreground">
        PulseQ — Built with Spring Boot, Redis, Kafka, LightGBM, React
      </footer>
    </div>
  )
}
