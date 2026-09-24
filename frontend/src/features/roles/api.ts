import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '@/api/httpClient'

export interface RoleView {
  id: string
  code: string
  name: string
  description: string | null
  systemRole: boolean
  permissions: string[]
  memberCount: number
}

export interface PermissionView {
  code: string
  description: string
}

export interface RoleInput {
  code?: string
  name?: string
  description?: string
  permissions?: string[]
}

const ROLES_KEY = ['roles'] as const

/** Readable by user-managers too, because they need it to assign roles. */
export function useRoles() {
  return useQuery({ queryKey: ROLES_KEY, queryFn: () => apiFetch<RoleView[]>('/roles') })
}

export function usePermissions(enabled: boolean) {
  return useQuery({
    queryKey: ['permissions'],
    queryFn: () => apiFetch<PermissionView[]>('/permissions'),
    enabled,
    staleTime: Infinity,
  })
}

export function useCreateRole() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: RoleInput) => apiFetch<RoleView>('/roles', { json: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ROLES_KEY }),
  })
}

export function useUpdateRole() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: string; input: RoleInput }) =>
      apiFetch<RoleView>(`/roles/${id}`, { method: 'PUT', json: input }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ROLES_KEY }),
  })
}

export function useDeleteRole() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiFetch<void>(`/roles/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ROLES_KEY }),
  })
}
