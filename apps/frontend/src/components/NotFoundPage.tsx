import { Link } from '@tanstack/react-router'
import { Card } from './ui'
import { NOT_FOUND_TITLE } from '../constants'
import { ROUTES } from '../routes'

export function NotFoundPage() {
  return (
    <Card>
      <Card.Header title={NOT_FOUND_TITLE} />
      <p>
        <Link to={ROUTES.HOME}>Return home</Link>
      </p>
    </Card>
  )
}
