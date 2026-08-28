import type { ButtonHTMLAttributes } from 'react'
import styles from './TabButton.module.css'

export interface TabButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  active?: boolean
}

export function TabButton({ active, className, ...props }: TabButtonProps) {
  return (
    <button
      className={[styles.tabBtn, active ? styles.active : '', className]
        .filter(Boolean)
        .join(' ')}
      {...props}
    />
  )
}
