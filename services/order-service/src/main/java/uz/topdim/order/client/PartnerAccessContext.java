package uz.topdim.order.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO контекста доступа партнёра из identity-service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartnerAccessContext {
    /** Роль в кабинете: OWNER, MANAGER, CASHIER */
    private String role;
    /** ID мерчанта */
    private Long merchantId;
    /** ID филиала (только для CASHIER) */
    private Long merchantLocationId;
    /** ID записи staff */
    private Long staffId;
    /** Имя сотрудника */
    private String staffName;
    /** Может ли видеть дашборд */
    private boolean canViewDashboard;
    /** Может ли погашать купоны */
    private boolean canRedeem;
}
