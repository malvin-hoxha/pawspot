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

export async function getPosts(): Promise<PostsPage> {
    const response = await fetch('/api/posts');

    if (!response.ok) {
        throw new Error(`Failed to fetch posts: HTTP ${response.status}`);
    }

    return response.json();
}