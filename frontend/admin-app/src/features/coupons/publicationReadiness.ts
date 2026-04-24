export interface MerchantPublicationLocation {
  address?: string;
  active?: boolean;
}

export interface MerchantPublicationSummary {
  id?: number;
  name?: string;
  primaryLocation?: MerchantPublicationLocation | null;
}

export interface PublicationReadinessResult {
  ready: boolean;
  reasons: string[];
}

export function getPublicationReadiness(
  merchant?: MerchantPublicationSummary | null,
): PublicationReadinessResult {
  const reasons: string[] = [];

  if (!merchant) {
    reasons.push('У купона не выбран мерчант');
  } else if (!merchant.primaryLocation) {
    reasons.push('У мерчанта нет primary location');
  } else {
    if (merchant.primaryLocation.active === false) {
      reasons.push('Primary location мерчанта неактивен');
    }
    if (!merchant.primaryLocation.address?.trim()) {
      reasons.push('В primary location не заполнен адрес');
    }
  }

  return { ready: reasons.length === 0, reasons };
}
