import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext, AuthProvider, useAuth } from '../auth/AuthContext'
import { ProtectedRoute } from '../auth/ProtectedRoute'
import { Modal } from '../components/Modal'
import { PublicGalleryPage } from '../pages/PublicGalleryPage'
import { AdminEventPage } from '../pages/AdminEventPage'
import { EventsPage } from '../pages/EventsPage'
import { LoginPage } from '../pages/LoginPage'
import { RegisterPage } from '../pages/RegisterPage'
import { validateFiles } from '../pages/TeamEventPage'
import { api, getApiError } from '../lib/api'
import * as apiModule from '../lib/api'

describe('critical frontend boundaries', () => {
  it('communicates and enforces the eight-character registration minimum', () => {
    render(
      <AuthContext.Provider value={{ user: null, register: vi.fn() }}>
        <MemoryRouter><RegisterPage /></MemoryRouter>
      </AuthContext.Provider>,
    )
    expect(screen.getByLabelText('Password')).toHaveAttribute('minlength', '8')
    expect(screen.getByText('Use 8–128 characters.')).toBeInTheDocument()
  })

  it('keeps focus in a modal input while controlled state updates', async () => {
    function ModalForm() {
      const [value, setValue] = useState('')
      return <Modal title="Create event" onClose={() => {}}><label htmlFor="event-name">Event name</label><input id="event-name" value={value} onChange={(event) => setValue(event.target.value)} /></Modal>
    }

    render(<ModalForm />)
    const input = screen.getByLabelText('Event name')
    await userEvent.click(input)
    await userEvent.type(input, 'Test Wedding')
    expect(input).toHaveValue('Test Wedding')
    expect(input).toHaveFocus()
  })

  it('restores a valid tab session and verifies it with the backend', async () => {
    const user = { id: 'admin-1', displayName: 'Restored Admin', role: 'ADMIN' }
    window.sessionStorage.setItem('photoshare.staffSession', JSON.stringify({ accessToken: 'valid-token', expiresIn: 900, expiresAt: Date.now() + 60_000, user }))
    const request = vi.spyOn(api, 'get').mockResolvedValue({ data: user })
    function SessionProbe() {
      const auth = useAuth()
      return <div>{auth.restoring ? 'Restoring session' : auth.user?.displayName}</div>
    }
    render(<AuthProvider><SessionProbe /></AuthProvider>)
    expect(await screen.findByText('Restored Admin')).toBeInTheDocument()
    expect(request).toHaveBeenCalledWith('/auth/me')
    request.mockRestore()
  })

  it('redirects a Team Member away from Admin routes', () => {
    render(
      <AuthContext.Provider value={{ user: { role: 'TEAM_MEMBER' } }}>
        <MemoryRouter initialEntries={['/admin/events']}>
          <Routes>
            <Route element={<ProtectedRoute role="ADMIN" />}><Route path="/admin/events" element={<div>Admin controls</div>} /></Route>
            <Route path="/team/events" element={<div>Assigned events</div>} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    )
    expect(screen.getByText('Assigned events')).toBeInTheDocument()
    expect(screen.queryByText('Admin controls')).not.toBeInTheDocument()
  })

  it('shows only the account-free PIN gate before public authorization', () => {
    render(<MemoryRouter initialEntries={['/gallery/demo']}><Routes><Route path="/gallery/:publicId" element={<PublicGalleryPage />} /></Routes></MemoryRouter>)
    expect(screen.getByRole('heading', { name: 'Enter gallery PIN' })).toBeInTheDocument()
    expect(screen.getByLabelText('Gallery PIN')).toBeInTheDocument()
    expect(screen.queryByText(/sign in|register/i)).not.toBeInTheDocument()
  })

  it('keeps protected gallery data hidden after an incorrect PIN', async () => {
    const request = vi.spyOn(api, 'post').mockRejectedValue({ response: { status: 401, data: { error: { code: 'INVALID_GALLERY_ACCESS', message: 'Gallery access could not be verified' } } } })
    render(<MemoryRouter initialEntries={['/gallery/demo']}><Routes><Route path="/gallery/:publicId" element={<PublicGalleryPage />} /></Routes></MemoryRouter>)
    await userEvent.type(screen.getByLabelText('Gallery PIN'), '000000')
    await userEvent.click(screen.getByRole('button', { name: 'Open gallery' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Gallery access could not be verified')
    expect(screen.queryByRole('heading', { name: /wedding|event/i })).not.toBeInTheDocument()
    request.mockRestore()
  })

  it('rejects unsafe queued files before upload', () => {
    const file = new File(['<svg></svg>'], 'unsafe.svg', { type: 'image/svg+xml' })
    const result = validateFiles([file])
    expect(result.accepted).toHaveLength(0)
    expect(result.error).toContain('use JPEG or PNG')
  })

  it('normalizes backend validation errors for forms', () => {
    expect(getApiError({ response: { data: { error: { fields: [{ message: 'Name is required' }] } } } }).message).toBe('Name is required')
  })

  it('loads the next public gallery page with the existing grant', async () => {
    vi.spyOn(apiModule, 'fetchProtectedImage').mockResolvedValue('blob:photo')
    const post = vi.spyOn(api, 'post').mockResolvedValue({ data: { accessToken: 'gallery-grant' } })
    const get = vi.spyOn(api, 'get')
      .mockResolvedValueOnce({ data: { title: 'Wedding', photos: [{ id: '1', contentPath: '/api/v1/public/galleries/demo/photos/1/content', widthPx: 2, heightPx: 2 }], page: 0, pageSize: 24, totalItems: 2, totalPages: 2, hasNext: true } })
      .mockResolvedValueOnce({ data: { title: 'Wedding', photos: [{ id: '2', contentPath: '/api/v1/public/galleries/demo/photos/2/content', widthPx: 2, heightPx: 2 }], page: 1, pageSize: 24, totalItems: 2, totalPages: 2, hasNext: false } })
    render(<MemoryRouter initialEntries={['/gallery/demo']}><Routes><Route path="/gallery/:publicId" element={<PublicGalleryPage />} /></Routes></MemoryRouter>)
    await userEvent.type(screen.getByLabelText('Gallery PIN'), '123456')
    await userEvent.click(screen.getByRole('button', { name: 'Open gallery' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Load more photos' }))
    expect(await screen.findByRole('button', { name: 'Open photo 2 of 2' })).toBeInTheDocument()
    expect(get).toHaveBeenLastCalledWith('/public/galleries/demo', {
      params: { page: 1, pageSize: 24 }, headers: { Authorization: 'Bearer gallery-grant' },
    })
    post.mockRestore()
    get.mockRestore()
    apiModule.fetchProtectedImage.mockRestore()
  })

  it('debounces Admin filename search and combines uploader and selection filters', async () => {
    const gallery = { id: '9', title: 'Wedding', status: 'DRAFT', photoIds: [], pinSet: false, publishedAt: null, expiresAt: null, shareUrl: null }
    const page = { items: [], page: 0, pageSize: 24, totalItems: 0, totalPages: 0, hasNext: false }
    const get = vi.spyOn(api, 'get').mockImplementation((url) => {
      if (url === '/events/1') return Promise.resolve({ data: { id: '1', name: 'Event' } })
      if (url === '/events/1/photos') return Promise.resolve({ data: page })
      if (url === '/events/1/members') return Promise.resolve({ data: { items: [{ user: { id: '7', displayName: 'Alex', email: 'alex@example.test' } }] } })
      if (url === '/team-members') return Promise.resolve({ data: { items: [] } })
      if (url === '/events/1/galleries') return Promise.resolve({ data: { items: [gallery] } })
      return Promise.reject(new Error(`Unexpected URL ${url}`))
    })
    render(<AuthContext.Provider value={{ token: 'staff-token', user: { role: 'ADMIN' } }}><MemoryRouter initialEntries={['/admin/events/1']}><Routes><Route path="/admin/events/:eventId" element={<AdminEventPage />} /></Routes></MemoryRouter></AuthContext.Provider>)
    await screen.findByRole('heading', { name: 'Event' })
    const search = screen.getByLabelText('Search filename')
    await userEvent.type(search, 'portrait')
    await userEvent.selectOptions(screen.getByLabelText('Uploader'), '7')
    await userEvent.selectOptions(screen.getByLabelText('Gallery selection'), 'selected')
    await waitFor(() => expect(get).toHaveBeenCalledWith('/events/1/photos', expect.objectContaining({
      params: { page: 0, pageSize: 24, search: 'portrait', uploaderId: '7', selected: true },
    })))
    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }))
    expect(search).toHaveValue('')
    get.mockRestore()
  })

  it('shows and saves optional gallery expiration in local time', async () => {
    const gallery = { id: '9', title: 'Wedding', status: 'PUBLISHED', photoIds: ['1'], pinSet: true, publishedAt: '2026-09-01T00:00:00Z', expiresAt: null, shareUrl: 'http://localhost/gallery/demo' }
    const get = vi.spyOn(api, 'get').mockImplementation((url) => {
      if (url === '/events/1') return Promise.resolve({ data: { id: '1', name: 'Event' } })
      if (url === '/events/1/photos') return Promise.resolve({ data: { items: [], page: 0, pageSize: 24, totalItems: 0, totalPages: 0, hasNext: false } })
      if (url === '/events/1/members' || url === '/team-members') return Promise.resolve({ data: { items: [] } })
      if (url === '/events/1/galleries') return Promise.resolve({ data: { items: [gallery] } })
      return Promise.reject(new Error(`Unexpected URL ${url}`))
    })
    const put = vi.spyOn(api, 'put').mockResolvedValue({ data: { ...gallery, expiresAt: '2030-01-01T10:30:00Z' } })
    render(<AuthContext.Provider value={{ token: 'staff-token', user: { role: 'ADMIN' } }}><MemoryRouter initialEntries={['/admin/events/1']}><Routes><Route path="/admin/events/:eventId" element={<AdminEventPage />} /></Routes></MemoryRouter></AuthContext.Provider>)
    const expiry = await screen.findByLabelText('Expiration (optional)')
    await userEvent.type(expiry, '2030-01-01T10:30')
    await userEvent.click(screen.getByRole('button', { name: 'Save expiration' }))
    expect(put).toHaveBeenCalledWith('/events/1/galleries/9/expiry', { expiresAt: expect.stringMatching(/^2030-01-01T/) })
    get.mockRestore()
    put.mockRestore()
  })
  it('shows cold start hint during slow login', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const loginPromise = new Promise(() => {}) // never resolves
    render(
      <AuthContext.Provider value={{ user: null, login: () => loginPromise, notice: '', clearNotice: () => {} }}>
        <MemoryRouter><LoginPage /></MemoryRouter>
      </AuthContext.Provider>,
    )
    
    await userEvent.type(screen.getByLabelText('Email'), 'admin@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'password')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    
    await waitFor(() => expect(screen.getByRole('button')).toHaveTextContent('Signing in…'))
    expect(screen.queryByText(/server is waking up/)).not.toBeInTheDocument()
    
    vi.advanceTimersByTime(8500)
    
    await waitFor(() => expect(screen.getByRole('button')).toHaveTextContent('Still connecting…'))
    expect(screen.getByText(/server is waking up/)).toBeInTheDocument()
    vi.useRealTimers()
  })

  it('separates error state from empty state on events page', async () => {
    let getReject
    const getPromise = new Promise((_, reject) => { getReject = reject })
    const get = vi.spyOn(api, 'get').mockReturnValue(getPromise)
    
    render(
      <AuthContext.Provider value={{ token: 'token', user: { role: 'ADMIN' } }}>
        <MemoryRouter><EventsPage role="ADMIN" /></MemoryRouter>
      </AuthContext.Provider>
    )
    
    expect(await screen.findByText('Loading events…')).toBeInTheDocument()
    
    getReject(new Error('Network Error'))
    
    expect(await screen.findByText('Unable to load events.')).toBeInTheDocument()
    expect(screen.queryByText('Create your first event')).not.toBeInTheDocument()
    
    get.mockRestore()
  })

  it('maintains filter values when focus is lost', async () => {
    const gallery = { id: '9', title: 'Wedding', status: 'DRAFT', photoIds: [], pinSet: false, publishedAt: null, expiresAt: null, shareUrl: null }
    const page = { items: [], page: 0, pageSize: 24, totalItems: 0, totalPages: 0, hasNext: false }
    const get = vi.spyOn(api, 'get').mockImplementation((url) => {
      if (url === '/events/1') return Promise.resolve({ data: { id: '1', name: 'Event' } })
      if (url === '/events/1/photos') return Promise.resolve({ data: page })
      if (url === '/events/1/members') return Promise.resolve({ data: { items: [{ user: { id: '7', displayName: 'Alex', email: 'alex@example.test' } }] } })
      if (url === '/team-members') return Promise.resolve({ data: { items: [] } })
      if (url === '/events/1/galleries') return Promise.resolve({ data: { items: [gallery] } })
      return Promise.reject(new Error(`Unexpected URL ${url}`))
    })
    
    render(
      <AuthContext.Provider value={{ token: 'staff-token', user: { role: 'ADMIN' } }}>
        <MemoryRouter initialEntries={['/admin/events/1']}>
          <Routes><Route path="/admin/events/:eventId" element={<AdminEventPage />} /></Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    )
    
    const search = await screen.findByLabelText('Search filename')
    await userEvent.type(search, 'portrait')
    expect(search).toHaveValue('portrait')
    
    search.blur()
    expect(search).toHaveValue('portrait')
    
    get.mockRestore()
  })

  it('validates file sizes and counts correctly', () => {
    const valid = new File(['x'], 'valid.jpg', { type: 'image/jpeg' })
    Object.defineProperty(valid, 'size', { value: 1024 * 1024 })
    const tooBig = new File(['y'], 'big.jpg', { type: 'image/jpeg' })
    Object.defineProperty(tooBig, 'size', { value: 5 * 1024 * 1024 })
    const wrongType = new File(['z'], 'wrong.txt', { type: 'text/plain' })

    const result = validateFiles([valid, tooBig, wrongType])
    expect(result.accepted).toHaveLength(1)
    expect(result.accepted[0].file).toBe(valid)
    expect(result.error).toContain('big.jpg: larger than 4 MB')
    expect(result.error).toContain('wrong.txt: use JPEG or PNG')

    const manyFiles = Array.from({ length: 21 }, () => valid)
    expect(validateFiles(manyFiles).error).toBe('Choose at most 20 photos at once.')
  })

  it('batches uploads sequentially to respect 4MB limits and isolates failures', async () => {
    const get = vi.spyOn(api, 'get').mockImplementation((url) => {
      if (url === '/events/1') return Promise.resolve({ data: { id: '1', name: 'Event' } })
      if (url === '/events/1/photos') return Promise.resolve({ data: { items: [], page: 0, pageSize: 24, totalItems: 0, totalPages: 0, hasNext: false } })
      return Promise.reject(new Error(`Unexpected URL ${url}`))
    })
    
    // Create 3 files: two 3MB files and one 1MB file.
    // Batching limit is 4MB raw. 
    // File 1 (3MB) -> Batch 1
    // File 2 (3MB) -> Batch 2
    // File 3 (1MB) -> Batch 2 (Wait, 3MB + 1MB = 4MB <= 4MB, so File 2 + 3 go to Batch 2).
    const f1 = new File(['a'], 'f1.jpg', { type: 'image/jpeg' }); Object.defineProperty(f1, 'size', { value: 3 * 1024 * 1024 })
    const f2 = new File(['b'], 'f2.jpg', { type: 'image/jpeg' }); Object.defineProperty(f2, 'size', { value: 3 * 1024 * 1024 })
    const f3 = new File(['c'], 'f3.jpg', { type: 'image/jpeg' }); Object.defineProperty(f3, 'size', { value: 1 * 1024 * 1024 })
    
    let postCallCount = 0
    const post = vi.spyOn(api, 'post').mockImplementation((url, formData) => {
      postCallCount++
      const files = formData.getAll('files')
      if (postCallCount === 1) {
        expect(files).toHaveLength(1)
        expect(files[0].name).toBe('f1.jpg')
        return Promise.reject(new Error('Network error on batch 1'))
      } else {
        expect(files).toHaveLength(2)
        expect(files[0].name).toBe('f2.jpg')
        expect(files[1].name).toBe('f3.jpg')
        return Promise.resolve({ data: { results: [{ status: 'UPLOADED' }, { status: 'FAILED', error: { message: 'Individual fail' } }] } })
      }
    })

    const { TeamEventPage } = await import('../pages/TeamEventPage')
    
    const { container } = render(
      <AuthContext.Provider value={{ token: 'token', user: { role: 'TEAM_MEMBER' } }}>
        <MemoryRouter initialEntries={['/team/events/1']}>
          <Routes><Route path="/team/events/:eventId" element={<TeamEventPage />} /></Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    )

    await waitFor(() => expect(screen.queryByText('Loading your uploads…')).not.toBeInTheDocument())
    
    const input = container.querySelector('#photo-files')
    await userEvent.upload(input, [f1, f2, f3])
    
    await userEvent.click(screen.getByRole('button', { name: 'Upload 3 photos' }))
    
    // Batch 1 fails, Batch 2 partially succeeds.
    // So f1 fails (network), f2 succeeds, f3 fails (individual).
    await waitFor(() => expect(screen.getByText(/1 uploaded; 2 failed/)).toBeInTheDocument())
    
    // Check retry uses the same batching (it retries the 2 failed files: f1 and f3)
    // f1 (3MB) + f3 (1MB) = 4MB -> Should fit in a single batch!
    post.mockImplementation((url, formData) => {
      const files = formData.getAll('files')
      expect(files).toHaveLength(2)
      expect(files[0].name).toBe('f1.jpg')
      expect(files[1].name).toBe('f3.jpg')
      return Promise.resolve({ data: { results: [{ status: 'UPLOADED' }, { status: 'UPLOADED' }] } })
    })

    await userEvent.click(screen.getByRole('button', { name: 'Retry 2 failed' }))
    
    await waitFor(() => expect(screen.getByText(/2 photos uploaded successfully/)).toBeInTheDocument())

    get.mockRestore()
    post.mockRestore()
  })
})
