import { apiClient } from './client'

export const reportReasons = {
  SPAM: 'Ανεπιθύμητο περιεχόμενο (spam)',
  MISLEADING_CONTENT: 'Παραπλανητικό περιεχόμενο',
  INAPPROPRIATE_CONTENT: 'Ακατάλληλο περιεχόμενο',
  PERSONAL_DATA: 'Δημοσίευση προσωπικών δεδομένων',
  OTHER: 'Άλλος λόγος',
} as const

export type ReportReason = keyof typeof reportReasons

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