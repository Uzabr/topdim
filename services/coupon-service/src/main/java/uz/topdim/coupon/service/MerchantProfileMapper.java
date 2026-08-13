package uz.topdim.coupon.service;

import org.springframework.stereotype.Component;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangePayload;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeResponse;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileChangeSummary;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileLocationPayload;
import uz.topdim.coupon.dto.merchantprofile.MerchantProfileLocationResponse;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.entity.MerchantLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeLocation;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static uz.topdim.coupon.util.PhoneUtils.normalize;

@Component
public class MerchantProfileMapper {

    public MerchantProfileChangeRequest fromPublished(
            Merchant merchant,
            List<MerchantLocation> publishedLocations,
            Long authorUserId,
            ResolvedPartnerAccess access
    ) {
        MerchantProfileChangeRequest request = MerchantProfileChangeRequest.builder()
                .merchant(merchant)
                .authorUserId(authorUserId)
                .authorStaffId(access.staffId())
                .authorRole(access.role())
                .baseProfileVersion(merchant.getProfileVersion())
                .status(MerchantProfileChangeStatus.DRAFT)
                .name(merchant.getName())
                .description(merchant.getDescription())
                .logoUrl(merchant.getLogoUrl())
                .coverUrl(merchant.getCoverUrl())
                .email(merchant.getEmail())
                .website(merchant.getWebsite())
                .contactPerson(merchant.getContactPerson())
                .build();

        for (int index = 0; index < publishedLocations.size(); index++) {
            MerchantLocation source = publishedLocations.get(index);
            request.getLocations().add(MerchantProfileChangeLocation.builder()
                    .request(request)
                    .sourceLocationId(source.getId())
                    .title(source.getTitle())
                    .address(source.getAddress())
                    .phone(source.getPhone())
                    .workingHours(source.getWorkingHours())
                    .latitude(source.getLatitude())
                    .longitude(source.getLongitude())
                    .primary(source.isPrimary())
                    .active(source.isActive())
                    .sortOrder(index)
                    .build());
        }
        return request;
    }

    public MerchantProfileChangeRequest copyRebased(
            MerchantProfileChangeRequest source,
            Merchant merchant,
            List<MerchantLocation> publishedLocations,
            Long authorUserId,
            ResolvedPartnerAccess access
    ) {
        MerchantProfileChangeRequest copy = MerchantProfileChangeRequest.builder()
                .merchant(merchant)
                .authorUserId(authorUserId)
                .authorStaffId(access.staffId())
                .authorRole(access.role())
                .baseProfileVersion(merchant.getProfileVersion())
                .status(MerchantProfileChangeStatus.DRAFT)
                .name(source.getName())
                .description(source.getDescription())
                .logoUrl(source.getLogoUrl())
                .coverUrl(source.getCoverUrl())
                .email(source.getEmail())
                .website(source.getWebsite())
                .contactPerson(source.getContactPerson())
                .build();

        Map<Long, MerchantProfileChangeLocation> sourceByPublishedId = new HashMap<>();
        List<MerchantProfileChangeLocation> requestedNewLocations = new ArrayList<>();
        source.getLocations().stream()
                .sorted(Comparator.comparingInt(MerchantProfileChangeLocation::getSortOrder))
                .forEach(location -> {
                    if (location.getSourceLocationId() == null) {
                        requestedNewLocations.add(location);
                    } else {
                        sourceByPublishedId.putIfAbsent(location.getSourceLocationId(), location);
                    }
                });

        int sortOrder = 0;
        for (MerchantLocation published : publishedLocations) {
            MerchantProfileChangeLocation requested = sourceByPublishedId.get(published.getId());
            MerchantProfileChangeLocation rebased = requested == null
                    ? fromPublishedLocation(copy, published, sortOrder)
                    : fromRequestedLocation(copy, requested, published.getId(), sortOrder);
            copy.getLocations().add(rebased);
            sortOrder++;
        }
        for (MerchantProfileChangeLocation requestedNew : requestedNewLocations) {
            copy.getLocations().add(fromRequestedLocation(copy, requestedNew, null, sortOrder));
            sortOrder++;
        }
        return copy;
    }

    public void applyPayload(
            MerchantProfileChangeRequest request,
            MerchantProfileChangePayload payload
    ) {
        if (payload.name() == null || payload.name().isBlank()) {
            throw new IllegalArgumentException("Название компании обязательно");
        }
        if (payload.locations() == null) {
            throw new IllegalArgumentException("Список филиалов обязателен");
        }

        request.setName(payload.name().trim());
        request.setDescription(trimToNull(payload.description()));
        request.setLogoUrl(trimToNull(payload.logoUrl()));
        request.setCoverUrl(trimToNull(payload.coverUrl()));
        request.setEmail(normalizeEmail(payload.email()));
        request.setWebsite(trimToNull(payload.website()));
        request.setContactPerson(trimToNull(payload.contactPerson()));

        request.getLocations().clear();
        Set<Long> sourceLocationIds = new HashSet<>();
        for (int index = 0; index < payload.locations().size(); index++) {
            MerchantProfileLocationPayload location = payload.locations().get(index);
            if (location.sourceLocationId() != null
                    && !sourceLocationIds.add(location.sourceLocationId())) {
                throw new IllegalArgumentException(
                        "Филиал указан более одного раза: " + location.sourceLocationId());
            }
            request.getLocations().add(MerchantProfileChangeLocation.builder()
                    .request(request)
                    .sourceLocationId(location.sourceLocationId())
                    .title(trimToNull(location.title()))
                    .address(trimToNull(location.address()))
                    .phone(normalize(location.phone()))
                    .workingHours(trimToNull(location.workingHours()))
                    .latitude(location.latitude())
                    .longitude(location.longitude())
                    .primary(location.primary())
                    .active(location.active())
                    .sortOrder(index)
                    .build());
        }
    }

    public MerchantProfileChangeResponse toResponse(MerchantProfileChangeRequest request) {
        List<MerchantProfileLocationResponse> locations = request.getLocations().stream()
                .sorted(java.util.Comparator.comparingInt(MerchantProfileChangeLocation::getSortOrder))
                .map(this::toLocationResponse)
                .toList();
        return new MerchantProfileChangeResponse(
                request.getId(),
                request.getMerchant().getId(),
                request.getAuthorUserId(),
                request.getAuthorStaffId(),
                request.getAuthorRole(),
                request.getBaseProfileVersion(),
                request.getStatus(),
                request.getAssigneeUserId(),
                request.getModerationComment(),
                request.getName(),
                request.getDescription(),
                request.getLogoUrl(),
                request.getCoverUrl(),
                request.getEmail(),
                request.getWebsite(),
                request.getContactPerson(),
                locations,
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getSubmittedAt(),
                request.getAssignedAt(),
                request.getDecidedAt(),
                request.getWithdrawnAt(),
                request.getLockVersion()
        );
    }

    public MerchantProfileChangeSummary toSummary(MerchantProfileChangeRequest request) {
        return new MerchantProfileChangeSummary(
                request.getId(),
                request.getMerchant().getId(),
                request.getName(),
                request.getStatus(),
                request.getBaseProfileVersion(),
                request.getAuthorUserId(),
                request.getAuthorStaffId(),
                request.getAuthorRole(),
                request.getAssigneeUserId(),
                request.getModerationComment(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getSubmittedAt(),
                request.getAssignedAt()
        );
    }

    private MerchantProfileLocationResponse toLocationResponse(
            MerchantProfileChangeLocation location
    ) {
        return new MerchantProfileLocationResponse(
                location.getId(),
                location.getSourceLocationId(),
                location.getTitle(),
                location.getAddress(),
                location.getPhone(),
                location.getWorkingHours(),
                location.getLatitude(),
                location.getLongitude(),
                location.isPrimary(),
                location.isActive(),
                location.getSortOrder()
        );
    }

    private MerchantProfileChangeLocation fromPublishedLocation(
            MerchantProfileChangeRequest request,
            MerchantLocation source,
            int sortOrder
    ) {
        return MerchantProfileChangeLocation.builder()
                .request(request)
                .sourceLocationId(source.getId())
                .title(source.getTitle())
                .address(source.getAddress())
                .phone(source.getPhone())
                .workingHours(source.getWorkingHours())
                .latitude(source.getLatitude())
                .longitude(source.getLongitude())
                .primary(source.isPrimary())
                .active(source.isActive())
                .sortOrder(sortOrder)
                .build();
    }

    private MerchantProfileChangeLocation fromRequestedLocation(
            MerchantProfileChangeRequest request,
            MerchantProfileChangeLocation source,
            Long sourceLocationId,
            int sortOrder
    ) {
        return MerchantProfileChangeLocation.builder()
                .request(request)
                .sourceLocationId(sourceLocationId)
                .title(source.getTitle())
                .address(source.getAddress())
                .phone(source.getPhone())
                .workingHours(source.getWorkingHours())
                .latitude(source.getLatitude())
                .longitude(source.getLongitude())
                .primary(source.isPrimary())
                .active(source.isActive())
                .sortOrder(sortOrder)
                .build();
    }

    private String normalizeEmail(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
