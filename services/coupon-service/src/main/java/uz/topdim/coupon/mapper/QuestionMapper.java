package uz.topdim.coupon.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.topdim.coupon.dto.QuestionResponse;
import uz.topdim.coupon.entity.CouponQuestion;

@Mapper(componentModel = "spring")
public interface QuestionMapper {

    @Mapping(target = "couponOfferId", source = "couponOffer.id")
    QuestionResponse toResponse(CouponQuestion question);
}
