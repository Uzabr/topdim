package uz.topdim.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.order.dto.AdminDashboardResponse;
import uz.topdim.order.entity.ComplaintStatus;
import uz.topdim.order.entity.Order;
import uz.topdim.order.entity.OrderStatus;
import uz.topdim.order.repository.ComplaintRepository;
import uz.topdim.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-04T05:00:00Z"),
            TASHKENT
    );
    private static final Set<OrderStatus> PAID_STATUSES = Set.of(
            OrderStatus.PAID,
            OrderStatus.COMPLETED,
            OrderStatus.REFUND_REQUESTED
    );

    @Mock private OrderRepository orderRepository;
    @Mock private ComplaintRepository complaintRepository;

    private AdminDashboardService service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardService(orderRepository, complaintRepository, CLOCK);
    }

    @Test
    void getDashboard_usesTashkentDayBoundariesAndFillsMissingSalesDays() {
        LocalDateTime today = LocalDate.of(2026, 8, 4).atStartOfDay();
        LocalDateTime tomorrow = today.plusDays(1);
        LocalDateTime sevenDayStart = LocalDate.of(2026, 7, 29).atStartOfDay();

        when(orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(today, tomorrow))
                .thenReturn(3L);
        when(complaintRepository.countByStatus(ComplaintStatus.PENDING)).thenReturn(2L);
        when(orderRepository.findPaidSalesByDay(sevenDayStart, tomorrow, PAID_STATUSES))
                .thenReturn(List.of(
                        sales(LocalDate.of(2026, 7, 29), 1L, "100000"),
                        sales(LocalDate.of(2026, 8, 2), 2L, "275000"),
                        sales(LocalDate.of(2026, 8, 4), 4L, "410000")
                ));
        when(orderRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of(
                order(9L, "ORD-9", OrderStatus.PAID, "90000", today.plusHours(9)),
                order(8L, "ORD-8", OrderStatus.PENDING, "120000", today.plusHours(8))
        ));

        AdminDashboardResponse result = service.getDashboard();

        assertThat(result.ordersToday()).isEqualTo(3);
        assertThat(result.paidRevenueToday()).isEqualByComparingTo("410000");
        assertThat(result.pendingComplaints()).isEqualTo(2);
        assertThat(result.salesLast7Days()).hasSize(7);
        assertThat(result.salesLast7Days()).extracting(AdminDashboardResponse.DailySales::date)
                .containsExactly(
                        LocalDate.of(2026, 7, 29),
                        LocalDate.of(2026, 7, 30),
                        LocalDate.of(2026, 7, 31),
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 2),
                        LocalDate.of(2026, 8, 3),
                        LocalDate.of(2026, 8, 4)
                );
        assertThat(result.salesLast7Days().get(1).orders()).isZero();
        assertThat(result.salesLast7Days().get(1).revenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.recentOrders()).extracting(AdminDashboardResponse.RecentOrder::orderNumber)
                .containsExactly("ORD-9", "ORD-8");
    }

    @Test
    void getDashboard_emptyDatabaseReturnsRealZeroesAndCompleteSeries() {
        LocalDateTime today = LocalDate.of(2026, 8, 4).atStartOfDay();
        LocalDateTime tomorrow = today.plusDays(1);
        LocalDateTime sevenDayStart = LocalDate.of(2026, 7, 29).atStartOfDay();

        when(orderRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(today, tomorrow))
                .thenReturn(0L);
        when(complaintRepository.countByStatus(ComplaintStatus.PENDING)).thenReturn(0L);
        when(orderRepository.findPaidSalesByDay(sevenDayStart, tomorrow, PAID_STATUSES))
                .thenReturn(List.of());
        when(orderRepository.findTop5ByOrderByCreatedAtDesc()).thenReturn(List.of());

        AdminDashboardResponse result = service.getDashboard();

        assertThat(result.ordersToday()).isZero();
        assertThat(result.paidRevenueToday()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.pendingComplaints()).isZero();
        assertThat(result.salesLast7Days()).hasSize(7)
                .allSatisfy(day -> {
                    assertThat(day.orders()).isZero();
                    assertThat(day.revenue()).isEqualByComparingTo(BigDecimal.ZERO);
                });
        assertThat(result.recentOrders()).isEmpty();
    }

    private OrderRepository.DailySalesProjection sales(
            LocalDate date,
            long orders,
            String revenue
    ) {
        return new OrderRepository.DailySalesProjection() {
            @Override public LocalDate getDate() { return date; }
            @Override public long getOrders() { return orders; }
            @Override public BigDecimal getRevenue() { return new BigDecimal(revenue); }
        };
    }

    private Order order(
            Long id,
            String orderNumber,
            OrderStatus status,
            String totalAmount,
            LocalDateTime createdAt
    ) {
        return Order.builder()
                .id(id)
                .orderNumber(orderNumber)
                .userId(10L)
                .userEmail("buyer@sizbiz.uz")
                .totalAmount(new BigDecimal(totalAmount))
                .status(status)
                .createdAt(createdAt)
                .build();
    }
}
