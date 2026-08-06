package uz.topdim.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.order.dto.AdminDashboardResponse;
import uz.topdim.order.entity.ComplaintStatus;
import uz.topdim.order.entity.Order;
import uz.topdim.order.entity.OrderStatus;
import uz.topdim.order.repository.ComplaintRepository;
import uz.topdim.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final int SALES_DAYS = 7;
    private static final Set<OrderStatus> PAID_STATUSES = Set.of(
            OrderStatus.PAID,
            OrderStatus.COMPLETED,
            OrderStatus.REFUND_REQUESTED
    );

    private final OrderRepository orderRepository;
    private final ComplaintRepository complaintRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        LocalDate currentDate = LocalDate.now(clock);
        LocalDate firstDate = currentDate.minusDays(SALES_DAYS - 1L);
        LocalDateTime today = currentDate.atStartOfDay();
        LocalDateTime tomorrow = currentDate.plusDays(1).atStartOfDay();
        LocalDateTime salesStart = firstDate.atStartOfDay();

        long ordersToday = orderRepository
                .countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(today, tomorrow);
        long pendingComplaints = complaintRepository.countByStatus(ComplaintStatus.PENDING);

        Map<LocalDate, OrderRepository.DailySalesProjection> persistedSales = orderRepository
                .findPaidSalesByDay(salesStart, tomorrow, PAID_STATUSES)
                .stream()
                .collect(Collectors.toMap(
                        OrderRepository.DailySalesProjection::getDate,
                        Function.identity()
                ));

        List<AdminDashboardResponse.DailySales> sales = new ArrayList<>(SALES_DAYS);
        for (int dayOffset = 0; dayOffset < SALES_DAYS; dayOffset++) {
            LocalDate date = firstDate.plusDays(dayOffset);
            OrderRepository.DailySalesProjection persisted = persistedSales.get(date);
            sales.add(new AdminDashboardResponse.DailySales(
                    date,
                    persisted == null ? 0 : persisted.getOrders(),
                    persisted == null || persisted.getRevenue() == null
                            ? BigDecimal.ZERO
                            : persisted.getRevenue()
            ));
        }

        BigDecimal paidRevenueToday = sales.get(SALES_DAYS - 1).revenue();
        List<AdminDashboardResponse.RecentOrder> recentOrders = orderRepository
                .findTop5ByOrderByCreatedAtDesc()
                .stream()
                .map(this::toRecentOrder)
                .toList();

        return new AdminDashboardResponse(
                ordersToday,
                paidRevenueToday,
                pendingComplaints,
                List.copyOf(sales),
                recentOrders
        );
    }

    private AdminDashboardResponse.RecentOrder toRecentOrder(Order order) {
        return new AdminDashboardResponse.RecentOrder(
                order.getId(),
                order.getOrderNumber(),
                order.getUserEmail(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}
