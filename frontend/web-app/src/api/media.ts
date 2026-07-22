import apiClient from './client';
import type { ApiResponse } from './client';

export interface UploadedFile {
  fileName: string;
  url: string;
}

export const mediaApi = {
  uploadFile: async (file: File): Promise<UploadedFile> => {
    const formData = new FormData();
    formData.append('file', file);
    // Do not set Content-Type manually: Axios adds multipart boundary in the browser.
    const response = await apiClient.post<ApiResponse<UploadedFile>>(
      '/api/v1/media/upload',
      formData,
    );
    return response.data.data;
  },
};
