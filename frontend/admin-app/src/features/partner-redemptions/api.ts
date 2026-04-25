import api from '../../api/client';
import type { ApiResponse, PageResponse } from '../../types';

export interface CreateRedemptionRequest {
  couponCode: string;
  staffName?: string;
}

export interface RedeemCouponResponse {
  purchasedCouponId: number;
  couponOfferId: number;
  couponOptionId: number;
  couponTitle: string;
  optionTitle: string;
  couponCode: string;
  status: 'ACTIVE' | 'USED' | 'EXPIRED' | 'CANCELLED';
  merchantId: number;
  merchantName?: string;
  purchasedAt?: string;
  expiresAt?: string;
  usedAt?: string;
}

export interface RedemptionResponse {
  id: number;
  couponTitle?: string;
  optionTitle?: string;
  couponCode?: string;
  redeemedByStaff?: string;
  note?: string;
  redeemedAt?: string;
}

export interface PartnerStatsResponse {
  totalCoupons: number;
  totalSold: number;
  totalRedeemed: number;
  totalRevenue: number;
}

export const partnerRedemptionsApi = {
  redeem: (request: CreateRedemptionRequest) =>
    api.post<ApiResponse<RedeemCouponResponse>>('/api/v1/partner/redemptions', request),

  getRedemptions: (page = 0, size = 10) =>
    api.get<ApiResponse<PageResponse<RedemptionResponse>>>('/api/v1/partner/redemptions', {
      params: { page, size },
    }),

  getStats: () =>
    api.get<ApiResponse<PartnerStatsResponse>>('/api/v1/partner/stats'),
};
