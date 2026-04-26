/** Types for admin merchant management */

export interface MerchantLocationResponse {
  id: number;
  title: string;
  address: string;
  phone: string;
  workingHours: string;
  latitude: number | null;
  longitude: number | null;
  primary: boolean;
  active: boolean;
}

export interface AdminMerchantSummary {
  id: number;
  name: string;
  logoUrl: string | null;
  contactPerson: string | null;
  email: string | null;
  userId: number | null;
  active: boolean;
  primaryLocation: MerchantLocationResponse | null;
  publicationReady: boolean;
  publicationBlockReason: string | null;
  activeCouponsCount: number;
  waitingCouponsCount: number;
  totalCouponsCount: number;
}

export interface MerchantDetail {
  id: number;
  name: string;
  description: string | null;
  logoUrl: string | null;
  coverUrl: string | null;
  email: string | null;
  website: string | null;
  contactPerson: string | null;
  userId: number | null;
  active: boolean;
  publicationReady: boolean;
  publicationBlockReason: string | null;
  primaryLocation: MerchantLocationResponse | null;
  locations: MerchantLocationResponse[];
}

export interface MerchantCoupon {
  id: number;
  title: string;
  status: string;
  totalSold: number;
  redeemedCount: number;
  buyUntil: string | null;
  useUntil: string | null;
  createdAt: string;
  options: { id: number; title: string; couponPrice: number }[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ApiResponse<T> {
  status: string;
  message: string;
  data: T;
}
