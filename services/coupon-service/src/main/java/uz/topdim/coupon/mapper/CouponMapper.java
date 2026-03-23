package uz.topdim.coupon.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.topdim.coupon.dto.CategoryResponse;
import uz.topdim.coupon.dto.MerchantResponse;
import uz.topdim.coupon.entity.Category;
import uz.topdim.coupon.entity.Merchant;

@Mapper(componentModel = "spring")
public interface CouponMapper {

    CategoryResponse toResponse(Category category);

    @Mapping(target = "active", source = "active")
    MerchantResponse toResponse(Merchant merchant);
}
