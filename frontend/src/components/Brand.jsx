import { Camera } from 'lucide-react'

export function Brand({ compact = false }) {
  return (
    <div className="brand" aria-label="PhotoShare">
      <Camera aria-hidden="true" size={compact ? 23 : 27} strokeWidth={1.8} />
      <span>PhotoShare</span>
    </div>
  )
}
