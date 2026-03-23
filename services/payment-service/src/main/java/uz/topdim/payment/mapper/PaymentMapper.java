package uz.topdim.payment.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.topdim.payment.dto.PaymentResponse;
import uz.topdim.payment.entity.Payment;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "statusName", expression = "java(payment.getStatus().name())")
    PaymentResponse toResponse(Payment payment);
}
