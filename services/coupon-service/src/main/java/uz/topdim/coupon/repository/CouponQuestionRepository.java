package uz.topdim.coupon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.coupon.entity.CouponQuestion;
import uz.topdim.coupon.entity.QuestionStatus;

public interface CouponQuestionRepository extends JpaRepository<CouponQuestion, Long> {

    @Query("""
            SELECT q
            FROM CouponQuestion q
            WHERE q.couponOffer.id = :couponOfferId
              AND q.status = :status
              AND q.answer IS NOT NULL
              AND TRIM(q.answer) <> ''
            ORDER BY q.createdAt DESC
            """)
    Page<CouponQuestion> findVisibleByCouponOfferIdAndStatus(
            @Param("couponOfferId") Long couponOfferId,
            @Param("status") QuestionStatus status,
            Pageable pageable
    );

    Page<CouponQuestion> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<CouponQuestion> findByStatusOrderByCreatedAtDesc(QuestionStatus status, Pageable pageable);
}
