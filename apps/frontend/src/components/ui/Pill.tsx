import type { ReactNode } from 'react'
import styles from './Pill.module.css'

export type PillVariant = 'urgent' | 'high' | 'normal' | 'positive' | 'info'

export interface PillProps {
  variant: PillVariant
  children: ReactNode
}

export function Pill({ variant, children }: PillProps) {
  return <span className={`${styles.pill} ${styles[variant]}`}>{children}</span>
}
