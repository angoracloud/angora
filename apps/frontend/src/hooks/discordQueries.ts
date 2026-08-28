import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { TIMING_CONFIG } from '../constants'
import { discordService } from '../services/discordService'
import type { DiscordServer } from '../types'

export const discordKeys = {
  servers: ['discord', 'servers'] as const,
  invite: ['discord', 'invite'] as const,
}

export function useDiscordServersQuery() {
  return useQuery({
    queryKey: discordKeys.servers,
    queryFn: discordService.getAllServers,
    refetchInterval: TIMING_CONFIG.BACKGROUND_POLL_INTERVAL_MS,
    refetchOnWindowFocus: true,
  })
}

export function useDiscordInviteQuery() {
  return useQuery({
    queryKey: discordKeys.invite,
    queryFn: discordService.getInviteInfo,
  })
}

export interface LeaveServerVariables {
  id: string
  serverName?: string
}

export function useLeaveServerMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id }: LeaveServerVariables) =>
      discordService.leaveServer(id),
    onSuccess: (_data, variables) => {
      queryClient.setQueryData<DiscordServer[]>(discordKeys.servers, (prev) =>
        prev?.map((s) =>
          s.id === variables.id || s.guildId === variables.id
            ? { ...s, botJoined: false }
            : s,
        ),
      )
    },
  })
}
