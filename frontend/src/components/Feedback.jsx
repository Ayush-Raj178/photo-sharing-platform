import { AlertTriangle, CheckCircle2, LoaderCircle } from 'lucide-react'

export function LoadingState({ label = 'Loading…' }) {
  return <div className="state-message" role="status"><LoaderCircle className="spin" aria-hidden="true" />{label}</div>
}

export function ErrorMessage({ error, onRetry }) {
  if (!error) return null
  return (
    <div className="alert alert-error" role="alert">
      <AlertTriangle aria-hidden="true" size={19} />
      <span>{typeof error === 'string' ? error : error.message}</span>
      {onRetry ? <button className="text-button" onClick={onRetry}>Try again</button> : null}
    </div>
  )
}

export function SuccessMessage({ children }) {
  if (!children) return null
  return <div className="alert alert-success" role="status"><CheckCircle2 aria-hidden="true" size={19} />{children}</div>
}

export function EmptyState({ icon: Icon, title, description, action }) {
  return (
    <div className="empty-state">
      {Icon ? <Icon aria-hidden="true" size={31} strokeWidth={1.6} /> : null}
      <h2>{title}</h2>
      <p>{description}</p>
      {action}
    </div>
  )
}
