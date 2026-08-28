import { Card } from '../ui'
import { PAGE_TITLES, SETTINGS_PAGE_COPY } from '../../copy'

export function SettingsPage() {
  return (
    <Card>
      <Card.Header title={PAGE_TITLES.SETTINGS} />
      <p>{SETTINGS_PAGE_COPY.BODY}</p>
    </Card>
  )
}
