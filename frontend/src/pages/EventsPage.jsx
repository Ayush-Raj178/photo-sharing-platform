import { ArrowRight, CalendarDays, Plus } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, getApiError } from '../lib/api'
import { AppShell } from '../components/AppShell'
import { EmptyState, ErrorMessage, LoadingState, SuccessMessage } from '../components/Feedback'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'

function formatDate(value) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value))
}

export function EventsPage({ role }) {
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [loaded, setLoaded] = useState(false)
  const [creating, setCreating] = useState(false)
  const [pending, setPending] = useState(false)
  const [success, setSuccess] = useState('')
  const [values, setValues] = useState({ name: '', description: '' })

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    setLoaded(false)
    try {
      setEvents((await api.get('/events')).data.items)
      setLoaded(true)
    } catch (nextError) { setError(getApiError(nextError, 'Unable to load events.')) }
    finally { setLoading(false) }
  }, [])
  useEffect(() => { load() }, [load])

  async function createEvent(event) {
    event.preventDefault()
    setPending(true)
    setError(null)
    try {
      const { data } = await api.post('/events', { name: values.name, description: values.description || null })
      setEvents((items) => [data, ...items])
      setCreating(false)
      setValues({ name: '', description: '' })
      setSuccess(`"${data.name}" was created.`)
    } catch (nextError) { setError(getApiError(nextError, 'Unable to create the event.')) }
    finally { setPending(false) }
  }

  const isAdmin = role === 'ADMIN'

  // State machine: loading → success(events) | success(empty) | error(retry)
  // "No assigned events yet" must ONLY appear when API succeeded with an empty list.
  function renderBody() {
    if (loading) return <LoadingState label="Loading events…" />
    if (error) return <div style={{ marginTop: 14 }}><ErrorMessage error={error} onRetry={load} /></div>
    if (loaded && events.length === 0) {
      return <EmptyState icon={CalendarDays} title={isAdmin ? 'Create your first event' : 'No assigned events yet'} description={isAdmin ? 'Events keep team uploads and customer galleries together.' : 'An Admin needs to assign you to an event before it appears here.'} action={isAdmin ? <button className="button button-primary" onClick={() => setCreating(true)}><Plus size={18} />Create event</button> : null} />
    }
    return (
      <div className="event-list">{events.map((item) => <Link key={item.id} className="event-row" to={`/${isAdmin ? 'admin' : 'team'}/events/${item.id}`}><div><h2>{item.name}</h2><p>{item.description || 'No description'} · Created {formatDate(item.createdAt)}</p></div><ArrowRight aria-hidden="true" /></Link>)}</div>
    )
  }

  return (
    <AppShell role={role}>
      <PageHeader title={isAdmin ? 'Events' : 'Assigned events'} description={isAdmin ? 'Create an event, assign your team, and shape the customer gallery.' : 'Open an event to upload and review your photographs.'} action={isAdmin ? <button className="button button-primary" onClick={() => setCreating(true)}><Plus size={18} /><span>Create event</span></button> : null} />
      <SuccessMessage>{success}</SuccessMessage>
      {renderBody()}
      {creating ? <Modal title="Create event" onClose={() => setCreating(false)}><form className="form-stack" onSubmit={createEvent}><div className="field"><label htmlFor="event-name">Event name</label><input id="event-name" className="input" required maxLength={150} value={values.name} onChange={(event) => setValues({ ...values, name: event.target.value })} /></div><div className="field"><label htmlFor="event-description">Description <span className="queue-muted">(optional)</span></label><textarea id="event-description" className="textarea" maxLength={2000} value={values.description} onChange={(event) => setValues({ ...values, description: event.target.value })} /></div><div className="form-actions"><button type="button" className="button button-secondary" onClick={() => setCreating(false)}>Cancel</button><button className="button button-primary" disabled={pending}>{pending ? 'Creating…' : 'Create event'}</button></div></form></Modal> : null}
    </AppShell>
  )
}
