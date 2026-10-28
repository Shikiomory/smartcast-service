const backendUrl = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '')

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(backendUrl + path, {...options, credentials: 'include'})
  const isJson = response.headers.get('content-type')?.includes('json')
  const body = isJson ? await response.json() : null
  if (!response.ok) {
    const messages: Record<number, string> = {
      401: 'Войдите в аккаунт заново.',
      403: 'У вас нет доступа к этой операции.',
      404: 'Материал не найден или больше недоступен.',
      409: 'Статус материала уже изменился. Обновите список.',
    }
    throw new ApiError(response.status, body?.detail || body?.message
      || messages[response.status] || `Ошибка запроса (${response.status}).`)
  }
  if (!isJson || response.redirected) {
    throw new ApiError(401, 'Сервер не вернул данные. Проверьте вход в аккаунт и адрес API.')
  }
  return body as T
}
