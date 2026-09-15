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

/** Returns true when the error is a transient network/timeout failure (no HTTP response received). */
export function isTransientError(error) {
  if (!error) return false
  if (error.code === 'ECONNABORTED' || error.code === 'ERR_NETWORK') return true
  if (!error.response && error.message) return true
  return false
}

// ---------------------------------------------------------------------------
// Concurrency-limited image fetching
// ---------------------------------------------------------------------------

const IMAGE_CONCURRENCY = 4
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
 * - max 4 concurrent requests
 * - 1 automatic retry on transient failure
 */
export function fetchProtectedImage(path, token, signal) {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) { reject(new DOMException('Aborted', 'AbortError')); return }
    const task = {
      execute: () => _fetchImageAttempt(path, token, signal)
        .then(resolve)
        .catch((firstError) => {
          if (signal?.aborted || firstError.code === 'ERR_CANCELED') {
            reject(firstError)
            return
          }
          // 1 bounded retry on transient errors only
          if (isTransientError(firstError)) {
            return _fetchImageAttempt(path, token, signal).then(resolve).catch(reject)
          }
          reject(firstError)
        }),
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
