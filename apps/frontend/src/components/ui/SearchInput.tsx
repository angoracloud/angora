import type { InputHTMLAttributes } from 'react'
import { Search } from 'lucide-react'
import styles from './SearchInput.module.css'

export type SearchInputProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'size' | 'type'
>

export function SearchInput({ className, ...props }: SearchInputProps) {
  return (
    <label className={[styles.search, className].filter(Boolean).join(' ')}>
      <Search size={15} aria-hidden="true" />
      <input type="search" {...props} />
    </label>
  )
}
