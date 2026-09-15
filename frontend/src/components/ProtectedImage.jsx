import { ImageOff, LoaderCircle, RotateCw } from 'lucide-react'
import { useEffect, useState, useRef } from 'react'
import { fetchProtectedImage } from '../lib/api'

export function ProtectedImage({ path, token, alt = '', className = '', onClick, onError }) {
  const [url, setUrl] = useState('')
  const [failed, setFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const [visible, setVisible] = useState(false)
  
  const containerRef = useRef(null)
  const onErrorRef = useRef(onError)
  
  useEffect(() => { onErrorRef.current = onError }, [onError])

  useEffect(() => {
    if (!window.IntersectionObserver) {
      setVisible(true)
      return
    }
    const observer = new IntersectionObserver((entries) => {
      if (entries[0].isIntersecting) {
        setVisible(true)
        observer.disconnect()
      }
    }, { rootMargin: '300px' })
    
    if (containerRef.current) observer.observe(containerRef.current)
    return () => observer.disconnect()
  }, [])

  useEffect(() => {
    if (!visible) return
    const controller = new AbortController()
    let objectUrl = ''
    setFailed(false)
    
    fetchProtectedImage(path, token, controller.signal)
      .then((nextUrl) => { objectUrl = nextUrl; setUrl(nextUrl) })
      .catch((error) => {
        if (error.code !== 'ERR_CANCELED') {
          setFailed(true)
          if (error.response?.status === 401) onErrorRef.current?.(error)
        }
      })
      
    return () => {
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [path, token, attempt, visible])

  if (failed) return <button type="button" ref={containerRef} className={`image-fallback ${className}`} onClick={() => setAttempt((value) => value + 1)}><ImageOff /><span>Image unavailable</span><RotateCw size={15} /></button>
  if (!url) return <div ref={containerRef} className={`image-loading ${className}`} role="status"><LoaderCircle className="spin" /><span className="sr-only">Loading image</span></div>
  return <img ref={containerRef} src={url} alt={alt} className={className} onClick={onClick} />
}
