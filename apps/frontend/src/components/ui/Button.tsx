import type { AnchorHTMLAttributes, ButtonHTMLAttributes } from 'react'
import styles from './Button.module.css'

export type ButtonVariant = 'primary' | 'secondary' | 'danger'
export type ButtonSize = 'sm' | 'md'

function classNames(...values: Array<string | false | undefined>): string {
  return values.filter(Boolean).join(' ')
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
}

export function Button({
  variant = 'secondary',
  size = 'md',
  className,
  ...props
}: ButtonProps) {
  return (
    <button
      className={classNames(
        styles.btn,
        styles[variant],
        styles[size],
        className,
      )}
      {...props}
    />
  )
}

export interface LinkButtonProps extends AnchorHTMLAttributes<HTMLAnchorElement> {
  variant?: ButtonVariant
  size?: ButtonSize
}

export function LinkButton({
  variant = 'secondary',
  size = 'md',
  className,
  ...props
}: LinkButtonProps) {
  return (
    <a
      className={classNames(
        styles.btn,
        styles[variant],
        styles[size],
        className,
      )}
      {...props}
    />
  )
}
