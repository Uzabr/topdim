package uz.topdim.coupon.util;

/**
 * Утилиты для нормализации телефонных номеров.
 * Canonical формат: только цифры с ведущим +.
 * Пример: "+998 90 123 45 67" → "+998901234567"
 */
public final class PhoneUtils {

    private PhoneUtils() {}

    /**
     * Нормализует телефонный номер: убирает все кроме цифр и +, гарантирует ведущий +.
     * Возвращает null если input null/blank или не содержит цифр.
     */
    public static String normalize(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String digits = phone.replaceAll("[^\\d+]", "");
        if (digits.isEmpty()) return null;
        return digits.startsWith("+") ? digits : "+" + digits;
    }
}
