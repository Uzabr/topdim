import api from '../../api';
import type {
  ApiResponse,
  CompanyChangePayload,
  CompanyChangeRequest,
  CompanyChangeSummary,
  CompanyRequestFilters,
  MediaUploadResponse,
  PageResponse,
  PublishedCompanyProfile,
} from './types';

const CHANGE_REQUESTS_PATH = '/api/v1/partner/merchant/change-requests';

function unwrap<T>(response: { data: ApiResponse<T> }): T {
  return response.data.data;
}

export const companyApi = {
  async getPublishedProfile(): Promise<PublishedCompanyProfile> {
    return unwrap(await api.get<ApiResponse<PublishedCompanyProfile>>(
      '/api/v1/partner/merchant',
    ));
  },

  async listRequests(
    filters: CompanyRequestFilters = {},
  ): Promise<PageResponse<CompanyChangeSummary>> {
    return unwrap(await api.get<ApiResponse<PageResponse<CompanyChangeSummary>>>(
      CHANGE_REQUESTS_PATH,
      { params: filters },
    ));
  },

  async createDraft(): Promise<CompanyChangeRequest> {
    return unwrap(await api.post<ApiResponse<CompanyChangeRequest>>(CHANGE_REQUESTS_PATH));
  },

  async getRequest(id: number): Promise<CompanyChangeRequest> {
    return unwrap(await api.get<ApiResponse<CompanyChangeRequest>>(
      `${CHANGE_REQUESTS_PATH}/${id}`,
    ));
  },

  async updateRequest(
    id: number,
    payload: CompanyChangePayload,
  ): Promise<CompanyChangeRequest> {
    return unwrap(await api.put<ApiResponse<CompanyChangeRequest>>(
      `${CHANGE_REQUESTS_PATH}/${id}`,
      payload,
    ));
  },

  async deleteDraft(id: number): Promise<void> {
    await api.delete(`${CHANGE_REQUESTS_PATH}/${id}`);
  },

  async submit(id: number): Promise<CompanyChangeRequest> {
    return unwrap(await api.post<ApiResponse<CompanyChangeRequest>>(
      `${CHANGE_REQUESTS_PATH}/${id}/submit`,
    ));
  },

  async withdraw(id: number, reason: string): Promise<CompanyChangeRequest> {
    return unwrap(await api.post<ApiResponse<CompanyChangeRequest>>(
      `${CHANGE_REQUESTS_PATH}/${id}/withdraw`,
      { reason },
    ));
  },

  async copy(id: number): Promise<CompanyChangeRequest> {
    return unwrap(await api.post<ApiResponse<CompanyChangeRequest>>(
      `${CHANGE_REQUESTS_PATH}/${id}/copy`,
    ));
  },

  async uploadMedia(file: File): Promise<MediaUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return unwrap(await api.post<ApiResponse<MediaUploadResponse>>(
      '/api/v1/media/upload',
      formData,
    ));
  },
};
