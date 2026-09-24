import type { DocumentView } from './types'

export function documentFixture(overrides: Partial<DocumentView> = {}): DocumentView {
  return {
    id: 'doc-1',
    caseId: 'c-1',
    checkId: 'ck-1',
    kind: 'CHECK_DOC',
    label: null,
    displayLabel: 'Original Document',
    originalFilename: 'degree.jpg',
    mimeType: 'image/jpeg',
    sizeBytes: 250_000,
    width: 1600,
    height: 1200,
    quality: 'HIGH',
    moveToNextPage: false,
    useLargerBox: false,
    crop: null,
    sortOrder: 0,
    version: 0,
    uploadedAt: '2026-09-24T10:00:00Z',
    ...overrides,
  }
}

export const pdfFixture = (overrides: Partial<DocumentView> = {}) =>
  documentFixture({
    id: 'doc-2',
    displayLabel: 'Additional Document 1',
    originalFilename: 'letter.pdf',
    mimeType: 'application/pdf',
    sizeBytes: 90_000,
    width: null,
    height: null,
    quality: null,
    sortOrder: 1,
    ...overrides,
  })
