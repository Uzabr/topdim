package uz.topdim.identity.service;

import org.springframework.stereotype.Service;
import uz.topdim.identity.entity.TrustLevel;
import uz.topdim.identity.entity.User;

/** L1 — вычисляемое состояние: телефон подтверждён ИЛИ была оплата. Не полагаемся на хранимый trust_level. */
@Service
public class TrustService {
    public TrustLevel computeTrustLevel(User user) {
        boolean verifiedPhone = user.isPhoneVerified();
        boolean everPaid = user.getPaidAt() != null;
        return (verifiedPhone || everPaid) ? TrustLevel.L1 : TrustLevel.L0;
    }
}
