package uz.topdim.coupon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа в Coupon Service.
 * Каталог купонов, категории, партнёры.
 * Порт: 8083
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class CouponServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CouponServiceApplication.class, args);
    }
}
