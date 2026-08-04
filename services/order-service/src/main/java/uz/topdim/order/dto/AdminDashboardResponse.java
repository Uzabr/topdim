package uz.topdim.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardResponse(
        long ordersToday,
        BigDecimal paidRevenueToday,
        long pendingComplaints,
        List<DailySales> salesLast7Days,
        List<RecentOrder> recentOrders
) {
    public record DailySales(
            LocalDate date,
            long orders,
            BigDecimal revenue
    ) {}

    public record RecentOrder(
            Long id,
            String orderNumber,
            String userEmail,
            String status,
            BigDecimal totalAmount,
            LocalDateTime createdAt
    ) {}
}
