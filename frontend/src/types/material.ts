export interface Material {
  id: number
  title: string
  description: string | null
  type: string
  status: string
  authorId: number
  authorName: string
  categoryId: number
  categoryName: string
  coverUrl: string | null
  durationMs: number | null
  linksExpireAt: string | null
}

export interface MaterialDetails extends Material {
  audioUrl: string | null
  audioContentType: string | null
  audioSizeBytes: number | null
}
