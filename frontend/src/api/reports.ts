import { apiClient } from './client'

export const reportReasons = {
  SPAM: 'Ανεπιθύμητο περιεχόμενο (spam)',
  MISLEADING_CONTENT: 'Παραπλανητικό περιεχόμενο',
  INAPPROPRIATE_CONTENT: 'Ακατάλληλο περιεχόμενο',
  PERSONAL_DATA: 'Δημοσίευση προσωπικών δεδομένων',
  OTHER: 'Άλλος λόγος',
} as const

export type ReportReason = keyof typeof reportReasons

export interface PostReportResponse {
    id: number
    reportedPostId: number
    postTitle: string | null
    reason: ReportReason
    description: string | null
    status: 'PENDING' | 'DISMISSED' | 'POST_REMOVED'
    createdAt: string
}

export interface PostReportsPage {
    content: PostReportResponse[]
    page: number
    size: number
    totalElements: number
    totalPages: number
    first: boolean
    last: boolean
}

export async function submitPostReport(
    postId: number,
    reason: ReportReason,
    description?: string
) {
    const response = await apiClient.post<{id: number}>(
        `/posts/${postId}/reports`,
        {reason,description}
    );

    return response.data;
}

export async function getPendingReports(page: number = 0): Promise<PostReportsPage> {
    const response = await apiClient.get<PostReportsPage>(
        '/admin/post-reports',
        {
            params: {page, size: 20}
        }
    );

    return response.data;
}

export async function dismissReport(reportId: number): Promise<void> {
    await apiClient.post(`/admin/post-reports/${reportId}/dismiss`);
}

export async function removeReportedPost(reportId: number): Promise<void> {
    await apiClient.post(`/admin/post-reports/${reportId}/remove-post`);
}