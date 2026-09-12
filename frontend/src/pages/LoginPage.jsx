import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Brand } from '../components/Brand'
import { ErrorMessage } from '../components/Feedback'
import { useAuth } from '../auth/AuthContext'

export function LoginPage() {
  const { user, login, notice, clearNotice } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [values, setValues] = useState({ email: '', password: '' })
  const [error, setError] = useState(null)
  const [pending, setPending] = useState(false)

  useEffect(() => () => clearNotice(), [clearNotice])
  if (user) return <Navigate to={user.role === 'ADMIN' ? '/admin/events' : '/team/events'} replace />

  async function submit(event) {
    event.preventDefault()
    setPending(true)
    setError(null)
    try {
      const nextUser = await login(values)
      const expectedPrefix = nextUser.role === 'ADMIN' ? '/admin/' : '/team/'
      const requested = location.state?.from
      navigate(requested?.startsWith(expectedPrefix) ? requested : `${expectedPrefix}events`, { replace: true })
    } catch (nextError) {
      setError(nextError)
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="auth-page">
      <section className="auth-pane">
        <Brand />
        <div className="auth-card">
          <h1>Welcome back</h1>
          <p>Sign in to continue to your event workspace.</p>
          <form className="form-stack" onSubmit={submit}>
            {notice ? <div className="alert alert-success" role="status">{notice}</div> : null}
            <ErrorMessage error={error} />
            <div className="field"><label htmlFor="email">Email</label><input className="input" id="email" type="email" autoComplete="email" required maxLength={254} value={values.email} onChange={(event) => setValues({ ...values, email: event.target.value })} /></div>
            <div className="field"><label htmlFor="password">Password</label><input className="input" id="password" type="password" autoComplete="current-password" required maxLength={128} value={values.password} onChange={(event) => setValues({ ...values, password: event.target.value })} /></div>
            <button className="button button-primary button-block" disabled={pending}>{pending ? 'Signing in…' : 'Sign in'}</button>
          </form>
          <p className="auth-switch">Admin without an account? <Link to="/register">Register</Link></p>
        </div>
      </section>
      <aside className="auth-visual" aria-hidden="true"><div className="auth-visual-copy"><h2>One place for every frame worth sharing.</h2><p>Keep event uploads private, choose the final story, and welcome customers through a protected gallery.</p></div></aside>
    </div>
  )
}
