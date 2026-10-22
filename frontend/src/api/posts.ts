import { apiClient } from "./client"

export interface PetPost {
  id: number
  type: 'LOST' | 'FOUND'
  title: string
  description: string
  species: string
  city: string
  area: string
  status: 'ACTIVE' | 'RESOLVED'
  imageUrl: string | null
  createdAt: string
  updatedAt: string
  userId: number
  userName: string
}

export interface PostsPage {
  content: PetPost[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface PostFilters {
  type?: 'LOST' | 'FOUND'
  species?: 'DOG' | 'CAT' | 'OTHER'
  city?: string
  page?: number
  size?: number
  sortBy?: string
  direction?: 'asc' | 'desc'
}

export async function getPosts(
  params?: PostFilters
): Promise<PostsPage> {
  const response = await apiClient.get<PostsPage>('/posts', {
    params,
  })

  return response.data
}