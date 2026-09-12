import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { Brand } from '../components/Brand'
import { ErrorMessage } from '../components/Feedback'
import { useAuth } from '../auth/AuthContext'

export function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [values, setValues] = useState({ displayName: '', email: '', password: '' })
  const [error, setError] = useState(null)
  const [pending, setPending] = useState(false)
  if (user) return <Navigate to="/admin/events" replace />

  async function submit(event) {
    event.preventDefault()
    setPending(true)
    setError(null)
    try {
      await register(values)
      navigate('/admin/events', { replace: true })
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
          <h1>Create your workspace</h1>
          <p>Register as an Admin to organize events and publish customer galleries.</p>
          <form className="form-stack" onSubmit={submit}>
            <ErrorMessage error={error} />
            <div className="field"><label htmlFor="displayName">Display name</label><input className="input" id="displayName" autoComplete="name" required maxLength={100} value={values.displayName} onChange={(event) => setValues({ ...values, displayName: event.target.value })} /></div>
            <div className="field"><label htmlFor="email">Email</label><input className="input" id="email" type="email" autoComplete="email" required maxLength={254} value={values.email} onChange={(event) => setValues({ ...values, email: event.target.value })} /></div>
            <div className="field"><label htmlFor="password">Password</label><input className="input" id="password" type="password" autoComplete="new-password" required minLength={8} maxLength={128} value={values.password} onChange={(event) => setValues({ ...values, password: event.target.value })} /><small>Use 8–128 characters.</small></div>
            <button className="button button-primary button-block" disabled={pending}>{pending ? 'Creating account…' : 'Create Admin account'}</button>
          </form>
          <p className="auth-switch">Already have an account? <Link to="/login">Sign in</Link></p>
        </div>
      </section>
      <aside className="auth-visual" aria-hidden="true"><div className="auth-visual-copy"><h2>From first upload to final gallery.</h2><p>A focused workspace for teams who care about every image and every customer handoff.</p></div></aside>
    </div>
  )
}
