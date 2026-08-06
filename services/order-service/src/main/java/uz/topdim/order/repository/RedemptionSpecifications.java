package uz.topdim.order.repository;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import uz.topdim.order.entity.Redemption;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedemptionSpecifications {

    public static Specification<Redemption> history(
            Long merchantId,
            Long staffId,
            String couponFragment,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("merchantId"), merchantId));

            if (staffId != null) {
                predicates.add(criteriaBuilder.equal(root.get("staffId"), staffId));
            }
            if (couponFragment != null) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.join("purchasedCoupon", JoinType.INNER).get("couponCode")),
                        "%" + couponFragment + "%",
                        '!'));
            }
            if (fromInclusive != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("redeemedAt"), fromInclusive));
            }
            if (toExclusive != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("redeemedAt"), toExclusive));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
