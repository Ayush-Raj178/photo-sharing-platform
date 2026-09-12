import { ArrowLeft, ImageOff } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Brand } from '../components/Brand'

export function NotFoundPage() {
  return <div className="pin-gate"><div className="pin-card"><Brand /><div className="pin-lock" style={{ marginTop: 44 }}><ImageOff /></div><h1 className="public-title">Page unavailable</h1><p>The page you requested could not be found.</p><Link className="button button-primary" to="/"><ArrowLeft size={18} />Return home</Link></div></div>
}
