package uz.topdim.coupon.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.topdim.coupon.dto.SituationResponse;
import uz.topdim.coupon.entity.Situation;

/**
 * MapStruct маппер для ситуаций.
 * Situation → SituationResponse (без couponCount — добавляется в сервисе).
 */
@Mapper(componentModel = "spring")
public interface SituationMapper {

    @Mapping(target = "key", source = "slug")
    @Mapping(target = "couponCount", ignore = true)
    SituationResponse toResponse(Situation situation);
}
