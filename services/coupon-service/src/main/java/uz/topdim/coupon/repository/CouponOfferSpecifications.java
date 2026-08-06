package uz.topdim.coupon.repository;

import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import uz.topdim.coupon.dto.AdminCouponFilter;
import uz.topdim.coupon.entity.CouponOffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CouponOfferSpecifications {

    public static Specification<CouponOffer> forAdmin(AdminCouponFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                predicates.add(root.get("status").in(filter.statuses()));
            }

            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + filter.search().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("merchant").get("name")), pattern)
                ));
            }

            if (filter.merchantId() != null) {
                predicates.add(cb.equal(root.get("merchant").get("id"), filter.merchantId()));
            }

            if (filter.assignedModeratorId() != null) {
                predicates.add(cb.equal(
                        root.get("assignedModeratorId"),
                        filter.assignedModeratorId()
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
