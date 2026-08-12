package uz.topdim.coupon.dto.merchantprofile;

public record MerchantProfileLocationResponse(
        Long id,
        Long sourceLocationId,
        String title,
        String address,
        String phone,
        String workingHours,
        Double latitude,
        Double longitude,
        boolean primary,
        boolean active,
        int sortOrder
) {
}
