import { X } from 'lucide-react'
import { useEffect, useRef } from 'react'

export function Modal({ title, children, onClose, wide = false }) {
  const closeRef = useRef(null)
  useEffect(() => {
    closeRef.current?.focus()
  }, [])

  useEffect(() => {
    const onKey = (event) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className={`modal ${wide ? 'modal-wide' : ''}`} role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <header><h2 id="modal-title">{title}</h2><button ref={closeRef} className="icon-button" onClick={onClose} aria-label="Close"><X /></button></header>
        {children}
      </section>
    </div>
  )
}
