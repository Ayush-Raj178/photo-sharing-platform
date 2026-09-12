import { ImageOff, LoaderCircle, RotateCw } from 'lucide-react'
import { useEffect, useState } from 'react'
import { fetchProtectedImage } from '../lib/api'

export function ProtectedImage({ path, token, alt = '', className = '', onClick, onError }) {
  const [url, setUrl] = useState('')
  const [failed, setFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    let objectUrl = ''
    setFailed(false)
    setUrl('')
    fetchProtectedImage(path, token, controller.signal)
      .then((nextUrl) => { objectUrl = nextUrl; setUrl(nextUrl) })
      .catch((error) => {
        if (error.code !== 'ERR_CANCELED') {
          setFailed(true)
          if (error.response?.status === 401) onError?.(error)
        }
      })
    return () => {
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [path, token, attempt, onError])

  if (failed) return <button type="button" className={`image-fallback ${className}`} onClick={() => setAttempt((value) => value + 1)}><ImageOff /><span>Image unavailable</span><RotateCw size={15} /></button>
  if (!url) return <div className={`image-loading ${className}`} role="status"><LoaderCircle className="spin" /><span className="sr-only">Loading image</span></div>
  return <img src={url} alt={alt} className={className} onClick={onClick} />
}
