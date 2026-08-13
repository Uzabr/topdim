package uz.topdim.coupon.dto.merchantprofile;

import java.util.List;

public record MerchantProfilePreflightResponse(
        boolean ready,
        List<BlockingLocation> blockingLocations
) {
    public record BlockingLocation(Long locationId, String title) {
    }
}
