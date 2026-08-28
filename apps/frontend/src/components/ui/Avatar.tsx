import styles from './Avatar.module.css'

export type AvatarSize = 'xs' | 'sm' | 'md' | 'lg'
export type AvatarColor =
  'blue' | 'teal' | 'violet' | 'orange' | 'amber' | 'cyan'

const COLORS: AvatarColor[] = [
  'blue',
  'teal',
  'violet',
  'orange',
  'amber',
  'cyan',
]

function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return ''
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  return (parts[0][0] + parts[1][0]).toUpperCase()
}

function hashColor(name: string): AvatarColor {
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 31 + name.charCodeAt(i)) | 0
  }
  return COLORS[Math.abs(hash) % COLORS.length]
}

export interface AvatarProps {
  name: string
  size?: AvatarSize
  color?: AvatarColor
  imageUrl?: string | null
}

export function Avatar({ name, size = 'md', color, imageUrl }: AvatarProps) {
  const resolvedColor = color ?? hashColor(name)

  if (imageUrl) {
    return (
      <span className={`${styles.avatar} ${styles[size]}`}>
        <img src={imageUrl} alt={name} />
      </span>
    )
  }

  return (
    <span
      className={`${styles.avatar} ${styles[size]} ${styles[resolvedColor]}`}
      aria-hidden="true"
    >
      {getInitials(name)}
    </span>
  )
}
