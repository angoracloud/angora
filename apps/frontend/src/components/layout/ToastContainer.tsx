import { useToast } from '../../hooks/useToast'
import type { ToastType } from '../../types'
import { TOAST_CONTAINER_COPY } from '../../copy'
import styles from './ToastContainer.module.css'

const TYPE_CLASS: Record<ToastType, string> = {
  error: styles.error,
  warning: styles.warning,
  success: styles.success,
  info: styles.info,
}

export function ToastContainer() {
  const { toasts, removeToast } = useToast()

  if (toasts.length === 0) return null

  return (
    <div
      className={styles.container}
      role="region"
      aria-label={TOAST_CONTAINER_COPY.REGION_LABEL}
      aria-live="polite"
    >
      {toasts.map((toast) => (
        <div
          key={toast.id}
          className={`${styles.toast} ${TYPE_CLASS[toast.type]}`}
        >
          <div className={styles.icon}>
            {toast.type === 'error' && '❌'}
            {toast.type === 'warning' && '⚠️'}
            {toast.type === 'success' && '✅'}
            {toast.type === 'info' && 'ℹ️'}
          </div>
          <div className={styles.content}>
            <div className={styles.title}>{toast.title}</div>
            <div className={styles.message}>{toast.message}</div>
          </div>
          <button
            className={styles.close}
            onClick={() => removeToast(toast.id)}
            aria-label={TOAST_CONTAINER_COPY.CLOSE_LABEL}
          >
            ✕
          </button>
        </div>
      ))}
    </div>
  )
}
