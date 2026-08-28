import type { ButtonHTMLAttributes } from 'react'
import type { LucideIcon } from 'lucide-react'
import styles from './IconButton.module.css'

export interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  icon: LucideIcon
  label: string
  withDot?: boolean
}

export function IconButton({
  icon: Icon,
  label,
  withDot,
  className,
  ...props
}: IconButtonProps) {
  return (
    <button
      className={[styles.iconBtn, className].filter(Boolean).join(' ')}
      aria-label={label}
      title={label}
      {...props}
    >
      <Icon size={16} />
      {withDot && <span className={styles.dot} />}
    </button>
  )
}
