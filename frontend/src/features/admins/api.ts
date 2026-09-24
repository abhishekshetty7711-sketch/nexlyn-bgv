import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '@/api/httpClient'
import type { Page } from '@/api/types'

export type AdminStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED'

export interface AdminView {
  id: string
  email: string
  fullName: string
  status: AdminStatus
  mfaEnabled: boolean
  lastLoginAt: string | null
  lockedUntil: string | null
  roles: string[]
}

export interface PendingInvitation {
  id: string
  email: string
  roles: string[]
  expiresAt: string
  createdAt: string
}

/** `inviteToken` exists only in this response: it is never stored and cannot be shown again. */
export interface IssuedInvitation {
  id: string
  email: string
  expiresAt: string
  inviteToken: string
}

export type AdminAction = 'disable' | 'enable' | 'unlock' | 'revoke-sessions'

export const PAGE_SIZE = 25

export function useAdmins(page: number) {
  return useQuery({
    queryKey: ['admins', page],
    queryFn: () => apiFetch<Page<AdminView>>(`/admins?page=${page}&size=${PAGE_SIZE}`),
  })
}

export function usePendingInvitations() {
  return useQuery({
    queryKey: ['invitations'],
    queryFn: () => apiFetch<PendingInvitation[]>('/admins/invitations'),
  })
}

export function useInviteAdmin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { email: string; roleIds: string[] }) =>
      apiFetch<IssuedInvitation>('/admins/invitations', { json: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['invitations'] }),
  })
}

export function useRevokeInvitation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiFetch<void>(`/admins/invitations/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['invitations'] }),
  })
}

export function useUpdateAdmin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, fullName, roleIds }: { id: string; fullName?: string; roleIds?: string[] }) =>
      apiFetch<AdminView>(`/admins/${id}`, { method: 'PUT', json: { fullName, roleIds } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admins'] }),
  })
}

export function useAdminAction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, action }: { id: string; action: AdminAction }) =>
      apiFetch<AdminView | undefined>(`/admins/${id}/${action}`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admins'] }),
  })
}
