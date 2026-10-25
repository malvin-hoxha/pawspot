import { apiClient } from "./client"

export interface PetPost {
  id: number
  type: 'LOST' | 'FOUND'
  title: string
  description: string
  species: 'DOG' | 'CAT' | 'OTHER'
  city: string
  area: string | null
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

export type UpdatePostInput = Partial<
  Pick<
    PetPost,
    'type' | 'title' | 'description' | 'species' |
    'city' | 'area' | 'status'
  >
>;

export interface CreatePostInput {
  type: PetPost['type']
  title: string
  description: string
  species: PetPost['species']
  city: string
  area?: string
  image: File
}

export async function getPosts(
  params?: PostFilters
): Promise<PostsPage> {
  const response = await apiClient.get<PostsPage>('/posts', {
    params,
  });

  return response.data;
}

export async function getPost(id: number): Promise<PetPost> {
  const response = await apiClient.get<PetPost>(`/posts/${id}`);
  return response.data;
}

export async function getMyPosts(page: number = 0): Promise<PostsPage> {
  const response = await apiClient.get<PostsPage>('/posts/my', {
    params: { page, size: 10 },
  });

  return response.data;
}

export async function deletePost(id: number): Promise<void> {
  await apiClient.delete(`/posts/${id}`);
}

export async function updatePost(id: number, updates: UpdatePostInput): Promise<PetPost> {
  const response = await apiClient.patch<PetPost>(`/posts/${id}`, updates);

  return response.data;
}

export async function createPost(input: CreatePostInput): Promise<PetPost> {
  const formData = new FormData();
  formData.append('type', input.type);
  formData.append('title', input.title);
  formData.append('description', input.description);
  formData.append('species', input.species);
  formData.append('city', input.city);

  if (input.area?.trim()) {
    formData.append('area', input.area.trim())
  }

  formData.append('image', input.image);

  const response = await apiClient.post<PetPost>('/posts', formData)
  return response.data
}
