import apiClient, { ApiResponse } from './client';

export interface PartnerApplicationData {
  firstName: string;
  lastName: string;
  phone: string;
  companyName: string;
  email?: string;
  city?: string;
  address?: string;
  workingHours?: string;
  businessCategory?: string;
  website?: string;
  telegramUsername?: string;
  comment?: string;
}

export const submitPartnerApplication = (data: PartnerApplicationData) =>
  apiClient.post<ApiResponse<unknown>>('/api/v1/partners/applications', data);
