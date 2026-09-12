import { Check, Clipboard, Eye, EyeOff, Image as ImageIcon, Plus, Users } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { AppShell } from '../components/AppShell'
import { EmptyState, ErrorMessage, LoadingState, SuccessMessage } from '../components/Feedback'
import { Modal } from '../components/Modal'
import { PageHeader } from '../components/PageHeader'
import { ProtectedImage } from '../components/ProtectedImage'
import { api, getApiError } from '../lib/api'

function sameIds(left = [], right = []) {
  return left.length === right.length && left.every((id, index) => id === right[index])
}

function toLocalDateTime(value) {
  if (!value) return ''
  const date = new Date(value)
  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

export function AdminEventPage() {
  const { eventId } = useParams()
  const { token } = useAuth()
  const [data, setData] = useState(null)
  const [selected, setSelected] = useState([])
  const [pin, setPin] = useState('')
  const [showPin, setShowPin] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [success, setSuccess] = useState('')
  const [pending, setPending] = useState('')
  const [memberModal, setMemberModal] = useState(false)
  const [memberMode, setMemberMode] = useState('create')
  const [memberValues, setMemberValues] = useState({ displayName: '', email: '', password: '', userId: '' })
  const [memberError, setMemberError] = useState(null)
  const [photoPage, setPhotoPage] = useState({ page: 0, pageSize: 24, totalItems: 0, totalPages: 0, hasNext: false })
  const [photoSearch, setPhotoSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [uploaderFilter, setUploaderFilter] = useState('')
  const [selectionFilter, setSelectionFilter] = useState('')
  const [photosLoading, setPhotosLoading] = useState(false)
  const [expiry, setExpiry] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [eventResult, photosResult, membersResult, rosterResult, galleriesResult] = await Promise.all([
        api.get(`/events/${eventId}`),
        api.get(`/events/${eventId}/photos`),
        api.get(`/events/${eventId}/members`),
        api.get('/team-members'),
        api.get(`/events/${eventId}/galleries`),
      ])
      const gallery = galleriesResult.data.items[0] || null
      setData({ event: eventResult.data, photos: photosResult.data.items, members: membersResult.data.items, roster: rosterResult.data.items, gallery })
      setPhotoPage(photosResult.data)
      setSelected(gallery?.photoIds || [])
      setExpiry(toLocalDateTime(gallery?.expiresAt))
    } catch (nextError) { setError(getApiError(nextError, 'This event workspace is unavailable.')) }
    finally { setLoading(false) }
  }, [eventId])

  useEffect(() => { load() }, [load])
  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(photoSearch.trim()), 300)
    return () => window.clearTimeout(timer)
  }, [photoSearch])

  const loadPhotos = useCallback(async (page = 0, append = false) => {
    setPhotosLoading(true)
    setError(null)
    try {
      const params = { page, pageSize: 24 }
      if (debouncedSearch) params.search = debouncedSearch
      if (uploaderFilter) params.uploaderId = uploaderFilter
      if (selectionFilter) params.selected = selectionFilter === 'selected'
      const result = await api.get(`/events/${eventId}/photos`, { params })
      setData((current) => current ? { ...current, photos: append ? [...current.photos, ...result.data.items] : result.data.items } : current)
      setPhotoPage(result.data)
    } catch (nextError) { setError(getApiError(nextError, 'Unable to load event photos.')) }
    finally { setPhotosLoading(false) }
  }, [debouncedSearch, eventId, selectionFilter, uploaderFilter])

  useEffect(() => {
    if (data) loadPhotos()
    // data is intentionally excluded: filters, not unrelated workspace updates, drive this request.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadPhotos])
  const gallery = data?.gallery
  const selectionChanged = gallery ? !sameIds(selected, gallery.photoIds) : false
  const memberNames = useMemo(() => new Map((data?.members || []).map((member) => [member.user.id, member.user.displayName])), [data?.members])
  const availableRoster = useMemo(() => {
    const assigned = new Set((data?.members || []).map((member) => member.user.id))
    return (data?.roster || []).filter((user) => !assigned.has(user.id))
  }, [data?.members, data?.roster])

  function togglePhoto(id) {
    setSelected((items) => items.includes(id) ? items.filter((item) => item !== id) : [...items, id])
    setSuccess('')
  }

  async function createGallery() {
    const title = window.prompt('Gallery title', data.event.name)
    if (!title?.trim()) return
    setPending('gallery')
    setError(null)
    try {
      const result = await api.post(`/events/${eventId}/galleries`, { title: title.trim() })
      setData((current) => ({ ...current, gallery: result.data }))
      setSelected([])
      setExpiry('')
      setSuccess('Gallery draft created.')
    } catch (nextError) { setError(getApiError(nextError, 'Unable to create the gallery.')) }
    finally { setPending('') }
  }

  async function saveSelection() {
    setPending('selection')
    setError(null)
    try {
      const result = await api.put(`/events/${eventId}/galleries/${gallery.id}/photos`, { photoIds: selected })
      setData((current) => ({ ...current, gallery: result.data }))
      setSuccess('Gallery selection saved.')
    } catch (nextError) { setError(getApiError(nextError, 'Unable to save the selection.')) }
    finally { setPending('') }
  }

  async function savePin() {
    if (!/^\d{6}$/.test(pin)) { setError({ message: 'Enter exactly six digits for the gallery PIN.' }); return }
    setPending('pin')
    setError(null)
    try {
      await api.put(`/events/${eventId}/galleries/${gallery.id}/pin`, { pin })
      setData((current) => ({ ...current, gallery: { ...current.gallery, pinSet: true } }))
      setPin('')
      setSuccess('Gallery PIN saved. Keep it somewhere safe; it cannot be recovered here.')
    } catch (nextError) { setError(getApiError(nextError, 'Unable to save the PIN.')) }
    finally { setPending('') }
  }

  async function saveExpiry() {
    setPending('expiry')
    setError(null)
    try {
      const result = await api.put(`/events/${eventId}/galleries/${gallery.id}/expiry`, {
        expiresAt: expiry ? new Date(expiry).toISOString() : null,
      })
      setData((current) => ({ ...current, gallery: result.data }))
      setExpiry(toLocalDateTime(result.data.expiresAt))
      setSuccess(expiry ? 'Gallery expiration saved.' : 'Gallery expiration removed.')
    } catch (nextError) { setError(getApiError(nextError, 'Unable to save gallery expiration.')) }
    finally { setPending('') }
  }

  function clearPhotoFilters() {
    setPhotoSearch('')
    setUploaderFilter('')
    setSelectionFilter('')
  }

  async function publish() {
    const isPublished = gallery.status === 'PUBLISHED'
    if (selected.length === 0) { setError({ message: 'Select at least one photo before publishing.' }); return }
    if (!isPublished && (!gallery.pinSet || selectionChanged)) { setError({ message: 'Save the selection and PIN before the first publication.' }); return }
    if (pin && !/^\d{6}$/.test(pin)) { setError({ message: 'A replacement PIN must contain exactly six digits.' }); return }
    const message = isPublished ? `Re-publish “${gallery.title}” with ${selected.length} selected photo${selected.length === 1 ? '' : 's'}?` : `Publish “${gallery.title}” with ${selected.length} selected photo${selected.length === 1 ? '' : 's'}?`
    if (!window.confirm(message)) return
    setPending('publish')
    setError(null)
    try {
      const body = isPublished ? { ...(selectionChanged ? { photoIds: selected } : {}), ...(pin ? { pin } : {}) } : {}
      const result = await api.post(`/events/${eventId}/galleries/${gallery.id}/publish`, body)
      setData((current) => ({ ...current, gallery: result.data }))
      setSelected(result.data.photoIds)
      setPin('')
      setSuccess(isPublished ? 'Gallery re-published successfully.' : 'Gallery published successfully.')
    } catch (nextError) { setError(getApiError(nextError, 'Unable to publish the gallery.')) }
    finally { setPending('') }
  }

  async function copyLink() {
    try { await navigator.clipboard.writeText(gallery.shareUrl); setSuccess('Gallery link copied. Share the PIN separately.') }
    catch { setError({ message: 'Copy failed. Select and copy the gallery link manually.' }) }
  }

  async function addMember(event) {
    event.preventDefault()
    setPending('member')
    setMemberError(null)
    try {
      const body = memberMode === 'existing' ? { userId: memberValues.userId } : { email: memberValues.email, displayName: memberValues.displayName, password: memberValues.password }
      const result = await api.post(`/events/${eventId}/members`, body)
      setData((current) => ({ ...current, members: [...current.members, result.data] }))
      setMemberModal(false)
      setMemberValues({ displayName: '', email: '', password: '', userId: '' })
      setSuccess(`${result.data.user.displayName} was assigned to this event.`)
      const roster = (await api.get('/team-members')).data.items
      setData((current) => ({ ...current, roster }))
    } catch (nextError) { setMemberError(getApiError(nextError, 'Unable to add the team member.')) }
    finally { setPending('') }
  }

  if (loading) return <AppShell role="ADMIN"><LoadingState label="Loading event workspace…" /></AppShell>
  if (!data) return <AppShell role="ADMIN"><PageHeader title="Event unavailable" /><ErrorMessage error={error} onRetry={load} /></AppShell>

  return (
    <AppShell role="ADMIN">
      <PageHeader eyebrow={[{ label: 'Events', to: '/admin/events' }, { label: data.event.name }]} title={data.event.name} description={data.event.description || 'Review team uploads and prepare the customer gallery.'} />
      <div style={{ display: 'grid', gap: 10, marginBottom: 18 }}><SuccessMessage>{success}</SuccessMessage><ErrorMessage error={error} /></div>
      <div className="workspace-grid">
        <section className="workspace-main" aria-labelledby="photos-title">
          <div className="photo-filters" aria-label="Photo filters"><div className="field"><label htmlFor="photo-search">Search filename</label><input id="photo-search" className="input" type="search" value={photoSearch} onChange={(event) => setPhotoSearch(event.target.value)} placeholder="Search photos" /></div><div className="field"><label htmlFor="uploader-filter">Uploader</label><select id="uploader-filter" className="select" value={uploaderFilter} onChange={(event) => setUploaderFilter(event.target.value)}><option value="">All uploaders</option>{data.members.map((member) => <option key={member.user.id} value={member.user.id}>{member.user.displayName}</option>)}</select></div><div className="field"><label htmlFor="selection-filter">Gallery selection</label><select id="selection-filter" className="select" value={selectionFilter} onChange={(event) => setSelectionFilter(event.target.value)}><option value="">All photos</option><option value="selected">Selected</option><option value="unselected">Unselected</option></select></div><button className="button button-secondary" type="button" onClick={clearPhotoFilters} disabled={!photoSearch && !uploaderFilter && !selectionFilter}>Clear filters</button></div>
          <div className="toolbar"><strong id="photos-title">{selected.length} selected</strong><div className="toolbar-actions"><button className="text-button" onClick={() => setSelected((current) => [...new Set([...current, ...data.photos.map((photo) => photo.id)])])}>Select loaded</button><button className="text-button" onClick={() => setSelected([])}>Clear selection</button><span className="queue-muted">{photoPage.totalItems} photo{photoPage.totalItems === 1 ? '' : 's'}</span></div></div>
          {photosLoading && data.photos.length === 0 ? <LoadingState label="Loading photos…" /> : data.photos.length === 0 ? <EmptyState icon={ImageIcon} title={photoPage.totalItems === 0 && !photoSearch && !uploaderFilter && !selectionFilter ? 'No event photos yet' : 'No photos match these filters'} description={photoPage.totalItems === 0 && !photoSearch && !uploaderFilter && !selectionFilter ? 'Assigned Team Members need to upload photographs before you can build the gallery.' : 'Clear or adjust the filters to see other event photos.'} /> : <><div className="photo-grid">{data.photos.map((photo) => {
            const isSelected = selected.includes(photo.id)
            return <article className="photo-item" key={photo.id}><div className="photo-frame"><button className={`photo-select ${isSelected ? 'photo-select-selected' : ''}`} aria-label={`${isSelected ? 'Remove' : 'Select'} ${photo.filename}`} aria-pressed={isSelected} onClick={() => togglePhoto(photo.id)}>{isSelected ? <Check size={18} /> : null}</button><ProtectedImage path={photo.contentPath} token={token} alt={photo.filename} /></div><div className="photo-meta"><strong title={photo.filename}>{photo.filename}</strong><small>{memberNames.get(photo.uploadedBy) || 'Team member'}</small></div></article>
          })}</div>{photoPage.hasNext ? <div className="load-more"><button className="button button-secondary" onClick={() => loadPhotos(photoPage.page + 1, true)} disabled={photosLoading}>{photosLoading ? 'Loading…' : 'Load more photos'}</button></div> : null}</>}
        </section>
        <aside className="workspace-side">
          <section className="panel">
            <div className="panel-section">
              <div className="panel-heading"><h2 className="panel-title">Gallery</h2>{gallery ? <span className="status-line"><i className="status-dot" />{gallery.status === 'PUBLISHED' ? 'Published' : 'Draft'}</span> : null}</div>
              {!gallery ? <><p className="selected-note">Create one gallery for this event, then choose its published photographs.</p><button className="button button-primary button-block" onClick={createGallery} disabled={pending === 'gallery'}>{pending === 'gallery' ? 'Creating…' : 'Create gallery'}</button></> : <div className="form-stack">
                <div className="field"><label>Gallery title</label><input className="input" value={gallery.title} readOnly /></div>
                <div className="field"><label htmlFor="gallery-pin">{gallery.status === 'PUBLISHED' ? 'Replacement PIN (optional)' : 'Gallery PIN'}</label><div className="input-with-action"><input id="gallery-pin" className="input" type={showPin ? 'text' : 'password'} inputMode="numeric" pattern="[0-9]{6}" maxLength={6} value={pin} onChange={(event) => setPin(event.target.value.replace(/\D/g, '').slice(0, 6))} placeholder={gallery.pinSet ? 'PIN is set' : 'Six digits'} /><button type="button" className="icon-button" onClick={() => setShowPin((value) => !value)} aria-label={showPin ? 'Hide PIN' : 'Show PIN'}>{showPin ? <EyeOff size={18} /> : <Eye size={18} />}</button></div>{gallery.status === 'PUBLISHED' ? <small>Rotating the PIN signs existing customer sessions out.</small> : null}</div>
                <div className="field"><label htmlFor="gallery-expiry">Expiration (optional)</label><input id="gallery-expiry" className="input" type="datetime-local" step="60" value={expiry} onChange={(event) => setExpiry(event.target.value)} /><small>Uses your local time; the server stores the corresponding UTC instant. Leave blank for no expiry.</small></div>
                <button type="button" className="button button-secondary button-block" onClick={saveExpiry} disabled={pending === 'expiry' || expiry === toLocalDateTime(gallery.expiresAt)}>{pending === 'expiry' ? 'Saving…' : expiry ? 'Save expiration' : gallery.expiresAt ? 'Remove expiration' : 'Keep no expiration'}</button>
                {gallery.status === 'DRAFT' ? <><button className="button button-secondary button-block" onClick={saveSelection} disabled={!selectionChanged || pending === 'selection'}>{pending === 'selection' ? 'Saving…' : selectionChanged ? 'Save selection' : 'Selection saved'}</button><button className="button button-secondary button-block" onClick={savePin} disabled={pin.length !== 6 || pending === 'pin'}>{pending === 'pin' ? 'Saving…' : gallery.pinSet ? 'Replace PIN' : 'Save PIN'}</button></> : null}
                {gallery.shareUrl ? <div className="field"><label>Gallery link (stable)</label><div className="share-row"><input className="input" readOnly value={gallery.shareUrl} aria-label="Gallery link" /><button onClick={copyLink} aria-label="Copy gallery link"><Clipboard size={18} /></button></div><small>Share the PIN separately.</small></div> : null}
                <button className="button button-primary button-block" onClick={publish} disabled={pending === 'publish' || (gallery.status === 'PUBLISHED' && !selectionChanged && !pin)}>{pending === 'publish' ? 'Publishing…' : gallery.status === 'PUBLISHED' ? 'Re-publish gallery' : 'Publish gallery'}</button>
                <p className="selected-note">{selected.length} photo{selected.length === 1 ? '' : 's'} staged. {selectionChanged ? 'Changes are not published yet.' : 'Published selection is current.'}</p>
              </div>}
            </div>
          </section>
          <section className="panel"><div className="panel-section"><div className="panel-heading"><h2 className="panel-title">Team</h2><button className="button button-secondary" onClick={() => setMemberModal(true)}><Plus size={16} />Add</button></div>{data.members.length === 0 ? <p className="selected-note">No team members assigned.</p> : <div className="member-list">{data.members.map((member) => <div className="member-row" key={member.user.id}><span className="profile-avatar">{member.user.displayName.slice(0, 1).toUpperCase()}</span><span><strong>{member.user.displayName}</strong><small>{member.user.email}</small></span></div>)}</div>}</div></section>
        </aside>
      </div>
      {memberModal ? <Modal title="Add team member" onClose={() => setMemberModal(false)}><div className="segmented"><button className={memberMode === 'create' ? 'active' : ''} onClick={() => setMemberMode('create')}>Create member</button><button className={memberMode === 'existing' ? 'active' : ''} onClick={() => setMemberMode('existing')}>Assign existing</button></div><form className="form-stack" onSubmit={addMember}><ErrorMessage error={memberError} />{memberMode === 'create' ? <><div className="field"><label htmlFor="member-name">Display name</label><input id="member-name" className="input" required maxLength={100} value={memberValues.displayName} onChange={(event) => setMemberValues({ ...memberValues, displayName: event.target.value })} /></div><div className="field"><label htmlFor="member-email">Email</label><input id="member-email" className="input" type="email" required maxLength={254} value={memberValues.email} onChange={(event) => setMemberValues({ ...memberValues, email: event.target.value })} /></div><div className="field"><label htmlFor="member-password">Initial password</label><input id="member-password" className="input" type="password" required minLength={8} maxLength={128} value={memberValues.password} onChange={(event) => setMemberValues({ ...memberValues, password: event.target.value })} /><small>Use 8–128 characters and communicate this password privately. It will not be shown again.</small></div></> : <div className="field"><label htmlFor="existing-member">Team member</label><select id="existing-member" className="select" required value={memberValues.userId} onChange={(event) => setMemberValues({ ...memberValues, userId: event.target.value })}><option value="">Choose a member</option>{availableRoster.map((user) => <option key={user.id} value={user.id}>{user.displayName} — {user.email}</option>)}</select>{availableRoster.length === 0 ? <small>Every roster member is already assigned to this event.</small> : null}</div>}<div className="form-actions"><button type="button" className="button button-secondary" onClick={() => setMemberModal(false)}>Cancel</button><button className="button button-primary" disabled={pending === 'member' || (memberMode === 'existing' && !memberValues.userId)}>{pending === 'member' ? 'Adding…' : 'Add to event'}</button></div></form></Modal> : null}
    </AppShell>
  )
}
