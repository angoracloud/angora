import { Card } from '../ui'
import { PAGE_TITLES, SETTINGS_PAGE_STRINGS } from '../../strings'

export function SettingsPage() {
  return (
    <Card>
      <Card.Header title={PAGE_TITLES.SETTINGS} />
      <p>{SETTINGS_PAGE_STRINGS.BODY}</p>
    </Card>
  )
}
