import { apiClient } from "./client"

export interface ConversationResponse {
    id: number,
    postId: number,
    postTitle: string,
    otherUserId: number,
    otherUserName: string,
    createdAt: string
}

export interface MessageResponse {
    id: number
    senderId: number
    senderName: string
    body: string
    createdAt: string
}

export async function startConversation(
    postId: number,
): Promise<ConversationResponse> {
    const response = await apiClient.post<ConversationResponse>(
        `/posts/${postId}/conversations`
    );

    return response.data;
}

export async function getConversation(
    conversationId: number
): Promise<ConversationResponse> {
    const response = await apiClient.get<ConversationResponse>(
        `/conversations/${conversationId}`
    );

    return response.data;
}

export async function getMessages(conversationId: number, page: number) {
  const response = await apiClient.get<{ 
        content: MessageResponse[]
        page: number
        last: boolean 
    }>(`/conversations/${conversationId}/messages`,{ 
        params: { page, size: 20 } },
  )

  return response.data;
}

export async function sendMessage(
    conversationId: number,
    body: string,
): Promise<MessageResponse> {
    const response = await apiClient.post<MessageResponse>(
        `/conversations/${conversationId}/messages`,
        { body },
    )

    return response.data
}

export async function getConversations(page: number) {
  const response = await apiClient.get<{
    content: ConversationResponse[]
    first: boolean
    last: boolean
  }>('/conversations', {
    params: { page, size: 20 },
  })

  return response.data
}