package uz.topdim.payment.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.topdim.payment.dto.PaymentResponse;
import uz.topdim.payment.entity.Payment;

/**
 * MapStruct маппер для платежей.
 * Payment → PaymentResponse.
 * paymentMode устанавливается контроллером из конфигурации.
 */
@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "statusName", expression = "java(payment.getStatus().name())")
    @Mapping(target = "paymentMode", ignore = true)
    PaymentResponse toResponse(Payment payment);
}
