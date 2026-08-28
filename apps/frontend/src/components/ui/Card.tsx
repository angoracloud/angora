/* eslint-disable react-refresh/only-export-components -- compound component
   API (Card.Header) intentionally bundles two components via Object.assign,
   which react-refresh's static analysis can't verify as fast-refresh-safe */
import type { ReactNode } from 'react'
import styles from './Card.module.css'

export type CardVariant = 'default' | 'danger'
export type CardPadding = 'none' | 'sm' | 'md'

const PADDING_CLASS: Record<CardPadding, string> = {
  none: 'paddingNone',
  sm: 'paddingSm',
  md: 'paddingMd',
}

function classNames(...values: Array<string | false | undefined>): string {
  return values.filter(Boolean).join(' ')
}

export interface CardProps {
  variant?: CardVariant
  padding?: CardPadding
  className?: string
  children: ReactNode
}

function CardRoot({
  variant = 'default',
  padding = 'md',
  className,
  children,
}: CardProps) {
  return (
    <div
      className={classNames(
        styles.card,
        variant === 'danger' && styles.danger,
        styles[PADDING_CLASS[padding]],
        className,
      )}
    >
      {children}
    </div>
  )
}

export interface CardHeaderProps {
  title: string
  action?: ReactNode
}

function CardHeader({ title, action }: CardHeaderProps) {
  return (
    <div className={styles.header}>
      <h3>{title}</h3>
      {action}
    </div>
  )
}

export const Card = Object.assign(CardRoot, { Header: CardHeader })
