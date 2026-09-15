import axios from 'axios'

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1').replace(/\/$/, '')

let staffToken = null
let unauthorizedHandler = null

// 60 s covers the measured ~29 s Vercel Hobby cold start with comfortable margin.
export const api = axios.create({ baseURL: API_BASE_URL, timeout: 60_000 })

api.interceptors.request.use((config) => {
  if (staffToken) config.headers.Authorization = `Bearer ${staffToken}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && staffToken && !error.config?.url?.startsWith('/public/')) {
      unauthorizedHandler?.()
    }
    return Promise.reject(error)
  },
)

export function configureStaffSession(token, onUnauthorized) {
  staffToken = token || null
  unauthorizedHandler = onUnauthorized || null
}

export function getApiError(error, fallback = 'Something went wrong. Please try again.') {
  const payload = error?.response?.data?.error
  const fields = payload?.fields?.map((item) => item.message).filter(Boolean) || []
  return {
    code: payload?.code || (error?.code === 'ECONNABORTED' ? 'REQUEST_TIMEOUT' : 'REQUEST_FAILED'),
    message: fields.length ? fields.join(' ') : payload?.message || fallback,
    retryAfter: error?.response?.headers?.['retry-after'],
    uncertain: !error?.response,
  }
}

export function isTransientError(error) {
  if (!error) return false
  if (error.code === 'ECONNABORTED' || error.code === 'ERR_NETWORK') return true
  if (!error.response && error.message) return true
  const status = error.response?.status
  if (status >= 500 && status < 600) return true
  if (status === 408 || status === 429) return true
  return false
}

// ---------------------------------------------------------------------------
// Concurrency-limited image fetching
// ---------------------------------------------------------------------------

const IMAGE_CONCURRENCY = 3
const IMAGE_TIMEOUT = 45_000
let _activeImageRequests = 0
const _imageQueue = []

function _processImageQueue() {
  while (_imageQueue.length > 0 && _activeImageRequests < IMAGE_CONCURRENCY) {
    const next = _imageQueue.shift()
    _activeImageRequests++
    next.execute().finally(() => {
      _activeImageRequests--
      _processImageQueue()
    })
  }
}

/**
 * Fetch a protected image through the backend proxy with:
 * - 45 s timeout
 * - max 3 concurrent requests
 * - up to 2 automatic retries on transient failures (500ms, 1000ms backoff)
 */
export function fetchProtectedImage(path, token, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) { reject(new DOMException('Aborted', 'AbortError')); return }
    const task = {
      execute: async () => {
        let lastError
        for (let attempt = 0; attempt < 3; attempt++) {
          try {
            const url = await _fetchImageAttempt(path, token, signal)
            resolve(url)
            return
          } catch (error) {
            if (signal?.aborted || error.code === 'ERR_CANCELED') {
              reject(error)
              return
            }
            lastError = error
            if (!isTransientError(error) || attempt === 2) {
              reject(error)
              return
            }
            
            const delay = attempt === 0 ? 500 : 1000
            let actualDelay = delay
            const retryAfter = error.response?.headers?.['retry-after']
            if (retryAfter) {
              const seconds = parseInt(retryAfter, 10)
              if (!Number.isNaN(seconds)) actualDelay = Math.max(delay, seconds * 1000)
            }
            
            await new Promise(r => setTimeout(r, actualDelay))
          }
        }
        reject(lastError)
      }
    }
    _imageQueue.push(task)
    _processImageQueue()
  })
}

function _fetchImageAttempt(path, token, signal) {
  return axios.get(`${API_BASE_URL}${path.replace('/api/v1', '')}`, {
    responseType: 'blob',
    timeout: IMAGE_TIMEOUT,
    signal,
    headers: { Authorization: `Bearer ${token}` },
  }).then((response) => URL.createObjectURL(response.data))
}

// ---------------------------------------------------------------------------
// Backend warm-up — fire-and-forget GET /api/health to wake Vercel container
// ---------------------------------------------------------------------------

let _warmUpPromise = null

/**
 * Non-blocking backend warm-up.  Safe to call multiple times; only the first
 * call actually fires the request.  Never throws — failures are silently
 * swallowed since this is purely an optimization.
 */
export function warmUpBackend() {
  if (_warmUpPromise) return _warmUpPromise
  const healthUrl = API_BASE_URL.replace(/\/api\/v1$/, '/api/health')
  _warmUpPromise = axios.get(healthUrl, { timeout: 65_000 }).catch(() => {})
  return _warmUpPromise
}
