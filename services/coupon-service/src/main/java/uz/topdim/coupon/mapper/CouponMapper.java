package uz.topdim.coupon.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.topdim.coupon.dto.CategoryResponse;
import uz.topdim.coupon.dto.MerchantResponse;
import uz.topdim.coupon.entity.Category;
import uz.topdim.coupon.entity.Merchant;

/**
 * MapStruct маппер для купонов.
 * Category → CategoryResponse, Merchant → MerchantResponse.
 */
@Mapper(componentModel = "spring")
public interface CouponMapper {

    CategoryResponse toResponse(Category category);

    @Mapping(target = "active", source = "active")
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "phone", ignore = true)
    @Mapping(target = "workingHours", ignore = true)
    @Mapping(target = "primaryLocation", ignore = true)
    @Mapping(target = "locations", ignore = true)
    MerchantResponse toResponse(Merchant merchant);
}
