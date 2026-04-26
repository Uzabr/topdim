package uz.topdim.coupon.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import uz.topdim.common.events.CouponRedeemedEvent;
import uz.topdim.coupon.service.CouponOfferService;

/**
 * Слушатель CouponRedeemedEvent из order-service.
 * Инкрементирует redeemedCount в CouponOffer идемпотентно.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponRedeemedListener {

    private final CouponOfferService couponOfferService;

    @RabbitListener(queues = "coupon.redeemed.queue")
    public void handleCouponRedeemed(CouponRedeemedEvent event) {
        log.info("Получен CouponRedeemedEvent: purchasedCouponId={}, couponOfferId={}, couponOptionId={}",
                event.getPurchasedCouponId(), event.getCouponOfferId(), event.getCouponOptionId());

        try {
            couponOfferService.incrementRedeemedOnce(
                    event.getPurchasedCouponId(),
                    event.getCouponOfferId(),
                    event.getCouponOptionId(),
                    event.getMerchantId()
            );
        } catch (Exception e) {
            log.error("Ошибка обработки CouponRedeemedEvent для purchasedCouponId={}: {}",
                    event.getPurchasedCouponId(), e.getMessage(), e);
        }
    }
}
