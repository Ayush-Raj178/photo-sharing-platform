import { CheckCircle2, Image as ImageIcon, RotateCw, UploadCloud, X, XCircle } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { AppShell } from '../components/AppShell'
import { EmptyState, ErrorMessage, LoadingState, SuccessMessage } from '../components/Feedback'
import { PageHeader } from '../components/PageHeader'
import { ProtectedImage } from '../components/ProtectedImage'
import { api, getApiError } from '../lib/api'

const MAX_FILE_SIZE = 10 * 1024 * 1024
const MAX_FILES = 20
const ALLOWED_TYPES = new Set(['image/jpeg', 'image/png'])
const formatBytes = (value) => value < 1024 * 1024 ? `${Math.ceil(value / 1024)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`

export function validateFiles(files) {
  if (files.length > MAX_FILES) return { accepted: [], error: `Choose at most ${MAX_FILES} photos at once.` }
  const accepted = []
  const rejected = []
  files.forEach((file) => {
    if (!ALLOWED_TYPES.has(file.type)) rejected.push(`${file.name}: use JPEG or PNG.`)
    else if (file.size > MAX_FILE_SIZE) rejected.push(`${file.name}: larger than 10 MB.`)
    else accepted.push({ id: crypto.randomUUID(), file, progress: 0, status: 'Queued', error: '' })
  })
  return { accepted, error: rejected.join(' ') }
}

export function TeamEventPage() {
  const { eventId } = useParams()
  const { token } = useAuth()
  const inputRef = useRef(null)
  const [event, setEvent] = useState(null)
  const [photos, setPhotos] = useState([])
  const [queue, setQueue] = useState([])
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [dragging, setDragging] = useState(false)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState('')
  const [photoPage, setPhotoPage] = useState({ page: 0, pageSize: 24, totalItems: 0, totalPages: 0, hasNext: false })
  const [photosLoading, setPhotosLoading] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [eventResult, photoResult] = await Promise.all([api.get(`/events/${eventId}`), api.get(`/events/${eventId}/photos`)])
      setEvent(eventResult.data)
      setPhotos(photoResult.data.items)
      setPhotoPage(photoResult.data)
    } catch (nextError) { setError(getApiError(nextError, 'This assigned event is unavailable.')) }
    finally { setLoading(false) }
  }, [eventId])
  useEffect(() => { load() }, [load])

  async function loadMorePhotos() {
    setPhotosLoading(true)
    setError(null)
    try {
      const result = await api.get(`/events/${eventId}/photos`, { params: { page: photoPage.page + 1, pageSize: 24 } })
      setPhotos((current) => [...current, ...result.data.items])
      setPhotoPage(result.data)
    } catch (nextError) { setError(getApiError(nextError, 'Unable to load more photos.')) }
    finally { setPhotosLoading(false) }
  }

  async function refreshPhotos() {
    const result = await api.get(`/events/${eventId}/photos`)
    setPhotos(result.data.items)
    setPhotoPage(result.data)
  }

  function addFiles(list) {
    const files = Array.from(list)
    const result = validateFiles(files)
    setError(result.error ? { message: result.error } : null)
    setQueue((items) => {
      if (items.length + result.accepted.length > MAX_FILES) {
        setError({ message: `The queue can contain at most ${MAX_FILES} photos.` })
        return items
      }
      return [...items, ...result.accepted]
    })
  }

  async function upload() {
    const pendingItems = queue.filter((item) => item.status === 'Queued' || item.status === 'Failed')
    if (!pendingItems.length) return
    setUploading(true)
    setError(null)
    setSuccess('')
    setQueue((items) => items.map((item) => pendingItems.some((candidate) => candidate.id === item.id) ? { ...item, status: 'Uploading', progress: 0, error: '' } : item))
    const form = new FormData()
    pendingItems.forEach((item) => form.append('files', item.file))
    try {
      const result = await api.post(`/events/${eventId}/photos`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (progressEvent) => {
          if (!progressEvent.total) return
          const progress = Math.min(99, Math.round(progressEvent.loaded * 100 / progressEvent.total))
          setQueue((items) => items.map((item) => pendingItems.some((candidate) => candidate.id === item.id) ? { ...item, progress } : item))
        },
      })
      setQueue((items) => items.map((item) => {
        const index = pendingItems.findIndex((candidate) => candidate.id === item.id)
        if (index < 0) return item
        const outcome = result.data.results[index]
        return outcome.status === 'UPLOADED' ? { ...item, status: 'Succeeded', progress: 100, error: '' } : { ...item, status: 'Failed', progress: 0, error: outcome.error?.message || 'Upload failed' }
      }))
      const succeeded = result.data.results.filter((item) => item.status === 'UPLOADED').length
      const failed = result.data.results.length - succeeded
      setSuccess(failed ? `${succeeded} uploaded; ${failed} failed. Review the results below.` : `${succeeded} photo${succeeded === 1 ? '' : 's'} uploaded successfully.`)
      await refreshPhotos()
    } catch (nextError) {
      const parsed = getApiError(nextError, 'Upload could not be completed.')
      setQueue((items) => items.map((item) => pendingItems.some((candidate) => candidate.id === item.id) ? { ...item, status: 'Failed', progress: 0, error: parsed.uncertain ? 'Outcome uncertain—review your photos before retrying.' : parsed.message } : item))
      setError(parsed.uncertain ? { message: 'The server response was lost. Your upload outcome is uncertain; the photo list was refreshed so you can review before retrying.' } : parsed)
      if (parsed.uncertain) {
        try { await refreshPhotos() } catch { /* primary error is already visible */ }
      }
    } finally { setUploading(false) }
  }

  const retryable = queue.filter((item) => item.status === 'Failed').length
  const pendingCount = queue.filter((item) => item.status === 'Queued' || item.status === 'Failed').length

  if (loading) return <AppShell role="TEAM_MEMBER"><LoadingState label="Loading your uploads…" /></AppShell>
  if (!event) return <AppShell role="TEAM_MEMBER"><PageHeader title="Event unavailable" /><ErrorMessage error={error} onRetry={load} /></AppShell>

  return (
    <AppShell role="TEAM_MEMBER">
      <PageHeader eyebrow={[{ label: 'Assigned events', to: '/team/events' }, { label: event.name }]} title="Your uploads" description={event.name} />
      <div style={{ display: 'grid', gap: 10, marginBottom: 18 }}><SuccessMessage>{success}</SuccessMessage><ErrorMessage error={error} /></div>
      <section className={`dropzone ${dragging ? 'dropzone-dragging' : ''}`} onDragOver={(event) => { event.preventDefault(); setDragging(true) }} onDragLeave={() => setDragging(false)} onDrop={(event) => { event.preventDefault(); setDragging(false); addFiles(event.dataTransfer.files) }}>
        <UploadCloud size={34} strokeWidth={1.5} aria-hidden="true" />
        <p>Drag and drop photos here</p>
        <input ref={inputRef} className="sr-only" id="photo-files" type="file" multiple accept="image/jpeg,image/png" onChange={(event) => { addFiles(event.target.files); event.target.value = '' }} />
        <button className="button button-primary" type="button" onClick={() => inputRef.current?.click()}>Choose photos</button>
        <small>JPEG or PNG · up to 10 MB each · 20 files</small>
      </section>
      {queue.length ? <section aria-labelledby="queue-title"><div className="queue-header"><h2 className="section-title" id="queue-title">Upload queue ({queue.length})</h2><button className="button button-primary" onClick={upload} disabled={uploading || !pendingCount}>{uploading ? 'Uploading…' : retryable && pendingCount === retryable ? `Retry ${retryable} failed` : `Upload ${pendingCount} photo${pendingCount === 1 ? '' : 's'}`}</button></div><div className="queue-list">{queue.map((item) => <div className="queue-row" key={item.id}><span className="queue-name" title={item.file.name}>{item.file.name}</span><span className="queue-muted">{formatBytes(item.file.size)}</span><div className="progress-cell"><div className="progress-track"><div className="progress-bar" style={{ width: `${item.progress}%` }} /></div></div><span className={`queue-status ${item.status === 'Failed' ? 'status-failed' : item.status === 'Succeeded' ? 'status-success' : ''}`}>{item.status === 'Succeeded' ? <CheckCircle2 size={16} /> : item.status === 'Failed' ? <XCircle size={16} /> : item.status === 'Uploading' ? <RotateCw className="spin" size={16} /> : null}{item.status}{item.error ? <span className="sr-only">: {item.error}</span> : null}</span><button className="icon-button" aria-label={`Remove ${item.file.name}`} disabled={uploading} onClick={() => setQueue((items) => items.filter((candidate) => candidate.id !== item.id))}><X size={17} /></button>{item.error ? <small className="status-failed" style={{ gridColumn: '1 / -1', paddingBottom: 8 }}>{item.error}</small> : null}</div>)}</div></section> : null}
      <section aria-labelledby="own-photos-title"><div className="section-heading-row"><h2 className="section-title" id="own-photos-title">Your photos ({photoPage.totalItems})</h2><span className="photo-note">Only photographs you uploaded for this event.</span></div>{photos.length === 0 ? <EmptyState icon={ImageIcon} title="No uploaded photos yet" description="Choose JPEG or PNG files above to add your first event photographs." /> : <><div className="photo-grid">{photos.map((photo) => <article className="photo-item" key={photo.id}><div className="photo-frame"><ProtectedImage path={photo.contentPath} token={token} alt={photo.filename} /></div><div className="photo-meta"><strong title={photo.filename}>{photo.filename}</strong><small>{formatBytes(photo.fileSizeBytes)}</small></div></article>)}</div>{photoPage.hasNext ? <div className="load-more"><button className="button button-secondary" onClick={loadMorePhotos} disabled={photosLoading}>{photosLoading ? 'Loading…' : 'Load more photos'}</button></div> : null}</>}</section>
    </AppShell>
  )
}
