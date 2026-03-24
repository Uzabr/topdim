package uz.topdim.bazaar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Точка входа в Bazaar Service.
 * Базары, магазины, геолокация.
 * Порт: 8088
 */
@SpringBootApplication
@EnableFeignClients
public class BazaarServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BazaarServiceApplication.class, args);
    }
}
