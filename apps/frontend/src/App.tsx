import { ToastProvider } from './context/ToastProvider'
import { useNavigation } from './hooks/useNavigation'
import { useDiscordServers } from './hooks/useDiscordServers'
import { Header } from './components/layout/Header'
import { ToastContainer } from './components/layout/ToastContainer'
import { HomePage } from './components/home/HomePage'
import { DiscordPage } from './components/discord/DiscordPage'
import { APP_ROUTES } from './constants'

function AppContent() {
  const { currentPath, navigate } = useNavigation()
  const { servers, inviteData, loading, error, leaveServer, deleteServer } =
    useDiscordServers()

  return (
    <div className="app-container">
      <Header currentPath={currentPath} onNavigate={navigate} />

      {currentPath === APP_ROUTES.HOME && <HomePage onNavigate={navigate} />}

      {currentPath === APP_ROUTES.DISCORD_BOT && (
        <DiscordPage
          servers={servers}
          inviteData={inviteData}
          loading={loading}
          error={error}
          onNavigate={navigate}
          onLeaveServer={leaveServer}
          onDeleteServer={deleteServer}
        />
      )}

      <ToastContainer />
    </div>
  )
}

export function App() {
  return (
    <ToastProvider>
      <AppContent />
    </ToastProvider>
  )
}
