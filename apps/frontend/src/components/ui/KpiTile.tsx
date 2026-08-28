import styles from './KpiTile.module.css'

export interface KpiTileTrend {
  direction: 'up' | 'down'
  value: string
}

export interface KpiTileProps {
  label: string
  value: string | number
  trend?: KpiTileTrend
  sparkline?: number[]
}

export function KpiTile({ label, value, trend, sparkline }: KpiTileProps) {
  const max = sparkline && sparkline.length > 0 ? Math.max(...sparkline) : 0

  return (
    <div className={styles.kpi}>
      <div className={styles.label}>{label}</div>
      <div className={styles.row}>
        <div className={styles.value}>{value}</div>
        {trend && (
          <span
            className={`${styles.trend} ${trend.direction === 'up' ? styles.trendUp : styles.trendDown}`}
          >
            {trend.direction === 'up' ? '▲' : '▼'} {trend.value}
          </span>
        )}
      </div>
      {sparkline && sparkline.length > 0 && (
        <div className={styles.sparkline} aria-hidden="true">
          {sparkline.map((v, i) => (
            <span
              key={i}
              className={
                i === sparkline.length - 1
                  ? `${styles.bar} ${styles.barHighlight}`
                  : styles.bar
              }
              style={{ height: max > 0 ? `${(v / max) * 100}%` : '0%' }}
            />
          ))}
        </div>
      )}
    </div>
  )
}
