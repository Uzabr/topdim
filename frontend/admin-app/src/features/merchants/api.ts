import api from '../../api/client';
import type {
  AdminMerchantSummary,
  MerchantDetail,
  PageResponse,
  ApiResponse,
  UpdateMerchantRequest,
} from './types';

/** Paginated merchant list with search/filter */
export const fetchMerchantPage = async (params: {
  page?: number;
  size?: number;
  search?: string;
  active?: boolean;
}) => {
  const res = await api.get<ApiResponse<PageResponse<AdminMerchantSummary>>>(
    '/api/v1/admin/merchants/page',
    { params },
  );
  return res.data.data;
};

/** Single merchant detail */
export const fetchMerchantDetail = async (id: number) => {
  const res = await api.get<ApiResponse<MerchantDetail>>(
    `/api/v1/admin/merchants/${id}`,
  );
  return res.data.data;
};

/** Update merchant */
export const updateMerchant = async (id: number, data: UpdateMerchantRequest) => {
  const res = await api.put<ApiResponse<MerchantDetail>>(
    `/api/v1/admin/merchants/${id}`,
    data,
  );
  return res.data.data;
};

/** Activate/deactivate */
export const setMerchantActive = async (id: number, active: boolean) => {
  const res = await api.patch<ApiResponse<MerchantDetail>>(
    `/api/v1/admin/merchants/${id}/active`,
    { active },
  );
  return res.data.data;
};

/** Merchant coupons */
export const fetchMerchantCoupons = async (
  id: number,
  params: { page?: number; size?: number; status?: string },
) => {
  const res = await api.get<ApiResponse<PageResponse<Record<string, unknown>>>>(
    `/api/v1/admin/merchants/${id}/coupons`,
    { params },
  );
  return res.data.data;
};
