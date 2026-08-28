import styles from './CountBadge.module.css'

export interface CountBadgeProps {
  count: number
  active?: boolean
}

export function CountBadge({ count, active }: CountBadgeProps) {
  return (
    <span className={`${styles.countBadge} ${active ? styles.active : ''}`}>
      {count}
    </span>
  )
}
