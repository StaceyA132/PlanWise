import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../authContext'

/** Login and register on one page, switched with a toggle. */
export function LoginPage() {
  const { isLoggedIn, login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const goTo = (location.state as { from?: string } | null)?.from ?? '/'

  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [income, setIncome] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (isLoggedIn) return <Navigate to={goTo} replace />

  async function handleSubmit(event: FormEvent) {
    event.preventDefault() // stop the browser's default full-page form submit
    setError(null)
    setSubmitting(true)
    try {
      if (mode === 'login') {
        await login(email, password)
      } else {
        await register(email, password, income === '' ? null : income)
      }
      navigate(goTo, { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setSubmitting(false)
    }
  }

  const isRegister = mode === 'register'
  return (
    <main className="auth-page">
      <div className="card auth-card">
        <h1 className="brand-large">PlanWise</h1>
        <p className="muted">Pay over time, with the full cost shown upfront.</p>

        <div className="tabs" role="tablist">
          <button type="button" role="tab" aria-selected={!isRegister} onClick={() => setMode('login')}>Log in</button>
          <button type="button" role="tab" aria-selected={isRegister} onClick={() => setMode('register')}>Create account</button>
        </div>

        <form onSubmit={handleSubmit} className="form">
          <label>
            Email
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                   required autoComplete="email" />
          </label>
          <label>
            Password
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                   required minLength={isRegister ? 8 : undefined}
                   autoComplete={isRegister ? 'new-password' : 'current-password'} />
          </label>
          {isRegister && (
            <label>
              <span>Monthly income <span className="muted">(optional, helps the assistant)</span></span>
              <input type="number" min="0" step="0.01" inputMode="decimal" value={income}
                     onChange={(e) => setIncome(e.target.value)} placeholder="4000.00" />
            </label>
          )}
          {error && <p className="error" role="alert">{error}</p>}
          <button type="submit" className="button" disabled={submitting}>
            {submitting ? 'Please wait…' : isRegister ? 'Create account' : 'Log in'}
          </button>
        </form>
      </div>
    </main>
  )
}
