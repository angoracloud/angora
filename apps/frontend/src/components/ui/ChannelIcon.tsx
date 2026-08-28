import styles from './ChannelIcon.module.css'

export type Channel = 'discord' | 'slack' | 'email' | 'web'

const LABELS: Record<Channel, string> = {
  discord: 'D',
  slack: 'S',
  email: '@',
  web: 'W',
}

export interface ChannelIconProps {
  channel: Channel
  label?: string
}

export function ChannelIcon({ channel, label }: ChannelIconProps) {
  return (
    <span className={`${styles.icon} ${styles[channel]}`}>
      {label ?? LABELS[channel]}
    </span>
  )
}
