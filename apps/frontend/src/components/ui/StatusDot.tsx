import styles from './StatusDot.module.css'

export type Status = 'open' | 'pending' | 'solved' | 'attention'

export interface StatusDotProps {
  status: Status
}

export function StatusDot({ status }: StatusDotProps) {
  return (
    <span className={`${styles.dot} ${styles[status]}`} aria-hidden="true" />
  )
}
