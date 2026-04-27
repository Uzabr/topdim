package uz.topdim.identity.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Ответ с контекстом доступа партнёра.
 * Используется внутренними сервисами для определения роли в кабинете.
 */
@Data
@Builder
public class PartnerAccessContextResponse {
    /** Роль в кабинете: OWNER, MANAGER, CASHIER */
    private String role;
    /** ID мерчанта */
    private Long merchantId;
    /** ID филиала (только для CASHIER) */
    private Long merchantLocationId;
    /** ID записи staff (только для staff) */
    private Long staffId;
    /** Имя сотрудника */
    private String staffName;
    /** Может ли видеть дашборд */
    private boolean canViewDashboard;
    /** Может ли погашать купоны */
    private boolean canRedeem;
}
