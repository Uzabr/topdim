package uz.topdim.identity.util;

import java.util.regex.Pattern;

/** Детект синтетического («затычка») email, который система генерит для аккаунтов без реального
 *  адреса (phone_/tg_/released_ @topdim.uz). Такой email нельзя показывать как контакт. */
public final class EmailPlaceholders {
    private static final Pattern PLACEHOLDER =
            Pattern.compile("^(tg_|phone_|released_).*@topdim\\.uz$");
    private EmailPlaceholders() {}

    public static boolean isPlaceholder(String email, boolean emailVerified) {
        return email != null && !emailVerified && PLACEHOLDER.matcher(email).matches();
    }
}
