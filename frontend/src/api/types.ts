/** Standard paged list body of the API (pages start at 0). */
export interface Page<T> {
  items: T[]
  page: number
  size: number
  total: number
}
