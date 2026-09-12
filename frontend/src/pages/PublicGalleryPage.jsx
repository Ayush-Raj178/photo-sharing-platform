import { ArrowLeft, ArrowRight, LockKeyhole, X } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Brand } from '../components/Brand'
import { ErrorMessage, LoadingState } from '../components/Feedback'
import { ProtectedImage } from '../components/ProtectedImage'
import { api, getApiError } from '../lib/api'

export function PublicGalleryPage() {
  const { publicId } = useParams()
  const [pin, setPin] = useState('')
  const [grant, setGrant] = useState('')
  const [gallery, setGallery] = useState(null)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState(null)
  const [unavailable, setUnavailable] = useState(false)
  const [active, setActive] = useState(null)
  const closeRef = useRef(null)

  const lockAgain = useCallback((message = 'Gallery access expired. Enter the PIN again.') => {
    setGrant('')
    setGallery(null)
    setActive(null)
    setPin('')
    setError({ message })
  }, [])

  async function openGallery(event) {
    event.preventDefault()
    if (!/^\d{6}$/.test(pin)) { setError({ message: 'Enter the six-digit PIN shared with you.' }); return }
    setPending(true)
    setError(null)
    setUnavailable(false)
    try {
      const access = await api.post(`/public/galleries/${publicId}/access`, { pin })
      const nextGrant = access.data.accessToken
      const result = await api.get(`/public/galleries/${publicId}`, { headers: { Authorization: `Bearer ${nextGrant}` } })
      setGrant(nextGrant)
      setGallery(result.data)
      setPin('')
    } catch (nextError) {
      const parsed = getApiError(nextError, 'Gallery access could not be verified.')
      if (nextError.response?.status === 404) setUnavailable(true)
      else setError({ ...parsed, message: parsed.retryAfter ? `${parsed.message} Try again in ${parsed.retryAfter} seconds.` : parsed.message })
    } finally { setPending(false) }
  }

  async function loadMorePhotos() {
    setPending(true)
    setError(null)
    try {
      const result = await api.get(`/public/galleries/${publicId}`, {
        params: { page: gallery.page + 1, pageSize: 24 },
        headers: { Authorization: `Bearer ${grant}` },
      })
      setGallery((current) => ({ ...result.data, photos: [...current.photos, ...result.data.photos] }))
    } catch (nextError) {
      const parsed = getApiError(nextError, 'More photos could not be loaded.')
      if ([401, 410].includes(nextError.response?.status)) lockAgain(parsed.message)
      else setError(parsed)
    } finally { setPending(false) }
  }

  useEffect(() => {
    if (active === null) return
    closeRef.current?.focus()
    const onKey = (event) => {
      if (event.key === 'Escape') setActive(null)
      if (event.key === 'ArrowLeft') setActive((index) => (index - 1 + gallery.photos.length) % gallery.photos.length)
      if (event.key === 'ArrowRight') setActive((index) => (index + 1) % gallery.photos.length)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [active, gallery?.photos.length])

  const imageToken = grant
  if (!gallery) {
    return <div className="public-page"><header className="public-header"><Brand compact /></header><main className="pin-gate"><section className="pin-card"><div className="pin-lock"><LockKeyhole size={34} strokeWidth={1.6} /></div><h1 className="public-title">{unavailable ? 'Gallery unavailable' : 'Enter gallery PIN'}</h1><p>{unavailable ? 'This gallery is not available. Check the link and try again.' : 'Use the six-digit PIN shared with you.'}</p>{!unavailable ? <form className="form-stack" onSubmit={openGallery}><div className="field"><label htmlFor="public-pin">Gallery PIN</label><input id="public-pin" className="input" type="password" inputMode="numeric" autoComplete="one-time-code" pattern="[0-9]{6}" maxLength={6} required value={pin} onChange={(event) => setPin(event.target.value.replace(/\D/g, '').slice(0, 6))} /></div><button className="button button-primary button-block" disabled={pending}>{pending ? 'Opening gallery…' : 'Open gallery'}</button><ErrorMessage error={error} /></form> : <button className="button button-secondary" onClick={() => { setUnavailable(false); setError(null) }}>Try PIN again</button>}</section></main></div>
  }

  return (
    <div className="public-page"><header className="public-header"><Brand compact /></header><main className="public-gallery"><header className="public-gallery-header"><h1 className="public-title">{gallery.title}</h1><p className="photo-note">{gallery.totalItems} published photo{gallery.totalItems === 1 ? '' : 's'}</p></header><ErrorMessage error={error} />{gallery.photos.length === 0 ? <LoadingState label="No published photos are available." /> : <><div className="public-grid">{gallery.photos.map((photo, index) => <button className="public-photo" key={photo.id} onClick={() => setActive(index)} aria-label={`Open photo ${index + 1} of ${gallery.totalItems}`}><ProtectedImage path={photo.contentPath} token={imageToken} alt="" /></button>)}</div>{gallery.hasNext ? <div className="load-more"><button className="button button-secondary" onClick={loadMorePhotos} disabled={pending}>{pending ? 'Loading…' : 'Load more photos'}</button></div> : null}</>}</main>{active !== null ? <div className="lightbox" role="dialog" aria-modal="true" aria-label={`Photo ${active + 1} of ${gallery.totalItems}`}><button className="lightbox-control" onClick={() => setActive((active - 1 + gallery.photos.length) % gallery.photos.length)} aria-label="Previous photo"><ArrowLeft /></button><ProtectedImage path={gallery.photos[active].contentPath} token={imageToken} alt={`Photo ${active + 1} of ${gallery.totalItems}`} className="lightbox-image" onError={() => lockAgain()} /><button className="lightbox-control" onClick={() => setActive((active + 1) % gallery.photos.length)} aria-label="Next photo"><ArrowRight /></button><button ref={closeRef} className="lightbox-control lightbox-close" onClick={() => setActive(null)} aria-label="Close preview"><X /></button><span className="lightbox-count">{active + 1} / {gallery.totalItems}</span></div> : null}</div>
  )
}
