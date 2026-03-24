package uz.topdim.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "redemptions")
@Getter @Setter @Builder @NoArgsConstructor /**
 * Запись о погашении купона.
 * Создаётся при сканировании QR кода партнёром.
 * Хранит merchantId, staffName и время погашения.
 */
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

    @Column
    private String redeemedByStaff;

    @Column
    private String note;

    @Column(nullable = false)
    private LocalDateTime redeemedAt;
}
