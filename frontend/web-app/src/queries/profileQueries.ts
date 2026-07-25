import { complaintsApi, type ComplaintData } from '../api/complaints';
import { reviewsApi, type ReviewData } from '../api/reviews';

const PROFILE_POLICY_PAGE_SIZE = 100;

interface ProfilePage<T> {
  data: {
    data: {
      content: T[];
      number: number;
      last: boolean;
    };
  };
}

type ProfilePageFetcher<T> = (
  page: number,
  size: number,
) => Promise<ProfilePage<T>>;

export async function loadAllProfilePages<T>(
  fetchPage: ProfilePageFetcher<T>,
): Promise<T[]> {
  const records: T[] = [];
  let page = 0;

  while (true) {
    const response = await fetchPage(page, PROFILE_POLICY_PAGE_SIZE);
    const current = response.data.data;
    records.push(...current.content);
    if (current.last) {
      return records;
    }
    page = current.number + 1;
  }
}

export const loadAllComplaints = () =>
  loadAllProfilePages<ComplaintData>((page, size) => complaintsApi.getMine(page, size));

export const loadAllReviews = () =>
  loadAllProfilePages<ReviewData>((page, size) => reviewsApi.getMine(page, size));

export const profileQueryKeys = {
  coupons: (userId: number) => ['my-coupons', userId, 'all'] as const,
  complaints: (userId: number) => ['my-complaints', userId] as const,
  reviews: (userId: number) => ['my-reviews', userId] as const,
  orders: (userId: number) => ['my-orders', userId] as const,
  notificationsRoot: (userId: number) => ['my-notifications', userId] as const,
  notifications: (userId: number, unreadOnly: boolean) =>
    ['my-notifications', userId, unreadOnly] as const,
};
