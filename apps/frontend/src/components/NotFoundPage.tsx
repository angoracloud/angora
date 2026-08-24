import { Link } from '@tanstack/react-router'
import { Card } from './ui'
import { ROUTES } from '../routes'

export function NotFoundPage() {
  return (
    <Card>
      <Card.Header title="Page not found" />
      <p>
        <Link to={ROUTES.HOME}>Return home</Link>
      </p>
    </Card>
  )
}
