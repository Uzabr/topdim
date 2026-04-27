package uz.topdim.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Запись о погашении купона.
 * Создаётся при сканировании QR кода или вводе PIN партнёром.
 * Хранит merchantId, merchantLocationId (филиал), staffId, staffName
 * и метод погашения (PIN/QR).
 */
@Entity
@Table(name = "redemptions")
@Getter @Setter @Builder @NoArgsConstructor
@AllArgsConstructor
public class Redemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchased_coupon_id", nullable = false)
    private PurchasedCoupon purchasedCoupon;

    @Column(nullable = false, unique = true, length = 32)
    private String redemptionCode;

    @Column(nullable = false)
    private Long merchantId;

    /** ID филиала, где произошло погашение */
    @Column(name = "merchant_location_id")
    private Long merchantLocationId;

    /** ID сотрудника из staff таблицы identity-service */
    @Column(name = "staff_id")
    private Long staffId;

    @Column
    private String redeemedByStaff;

    /** Метод погашения: PIN или QR */
    @Column(name = "redeem_method", length = 10)
    private String redeemMethod;

    @Column
    private String note;

    @Column(nullable = false)
    private LocalDateTime redeemedAt;
}
