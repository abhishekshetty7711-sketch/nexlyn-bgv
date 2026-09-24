import { useState } from 'react'
import { describeError } from '@/api/errors'
import { Alert } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Spinner } from '@/components/ui/spinner'
import { useClients } from '../api'
import { useAddCheck, useCheckTypes } from './api'
import type { CheckView } from './types'

interface AddCheckDialogProps {
  caseId: string
  clientId: string
  onAdded: (check: CheckView) => void
  onClose: () => void
}

/** Pick a kind of check to add. The client's usual checks come first. */
export function AddCheckDialog({ caseId, clientId, onAdded, onClose }: AddCheckDialogProps) {
  const types = useCheckTypes()
  const clients = useClients()
  const add = useAddCheck(caseId)
  const [error, setError] = useState<string | null>(null)

  const usual = clients.data?.find((client) => client.id === clientId)?.defaultCheckTypes ?? []
  const all = [...(types.data ?? [])].sort((a, b) => a.order - b.order)
  const usualTypes = usual.flatMap((code) => all.filter((type) => type.code === code))
  const others = all.filter((type) => !usual.includes(type.code))

  async function choose(code: string) {
    setError(null)
    try {
      onAdded(await add.mutateAsync(code))
    } catch (problem) {
      setError(describeError(problem))
    }
  }

  function row(code: string, name: string, document: string) {
    return (
      <li key={code}>
        <button
          type="button"
          disabled={add.isPending}
          onClick={() => choose(code)}
          className="flex w-full flex-col rounded-md border border-slate-200 px-3 py-2 text-left hover:bg-slate-50 disabled:opacity-50"
        >
          <span className="text-sm font-medium text-slate-900">{name}</span>
          <span className="text-xs text-slate-500">{document}</span>
        </button>
      </li>
    )
  }

  return (
    <Dialog title="Add a check" onClose={onClose}>
      {types.isLoading && <Spinner />}
      {types.isError && <Alert variant="error">{describeError(types.error)}</Alert>}
      {error && <Alert variant="error">{error}</Alert>}
      {usualTypes.length > 0 && (
        <div className="mb-4">
          <h3 className="mb-2 text-sm font-semibold text-slate-800">This client usually asks for</h3>
          <ul className="flex flex-col gap-2">{usualTypes.map((type) => row(type.code, type.displayName, type.documentName))}</ul>
        </div>
      )}
      {others.length > 0 && (
        <div className="mb-4">
          <h3 className="mb-2 text-sm font-semibold text-slate-800">{usualTypes.length > 0 ? 'Other checks' : 'All checks'}</h3>
          <ul className="flex flex-col gap-2">{others.map((type) => row(type.code, type.displayName, type.documentName))}</ul>
        </div>
      )}
      <div className="flex justify-end">
        <Button variant="outline" onClick={onClose}>
          Cancel
        </Button>
      </div>
    </Dialog>
  )
}
