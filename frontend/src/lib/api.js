import axios from 'axios'

export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1').replace(/\/$/, '')

let staffToken = null
let unauthorizedHandler = null

export const api = axios.create({ baseURL: API_BASE_URL, timeout: 30000 })

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

export async function fetchProtectedImage(path, token, signal) {
  const response = await axios.get(`${API_BASE_URL}${path.replace('/api/v1', '')}`, {
    responseType: 'blob',
    signal,
    headers: { Authorization: `Bearer ${token}` },
  })
  return URL.createObjectURL(response.data)
}
