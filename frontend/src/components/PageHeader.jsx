import { Link } from 'react-router-dom'

export function PageHeader({ eyebrow, title, description, action }) {
  return (
    <header className="page-header">
      <div>
        {eyebrow ? <div className="breadcrumb">{eyebrow.map((item, index) => <span key={`${item.label}-${index}`}>{item.to ? <Link to={item.to}>{item.label}</Link> : item.label}{index < eyebrow.length - 1 ? <i>/</i> : null}</span>)}</div> : null}
        <h1>{title}</h1>
        {description ? <p>{description}</p> : null}
      </div>
      {action}
    </header>
  )
}
