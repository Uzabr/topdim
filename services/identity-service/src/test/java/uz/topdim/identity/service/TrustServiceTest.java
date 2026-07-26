package uz.topdim.identity.service;

import org.junit.jupiter.api.Test;
import uz.topdim.identity.entity.TrustLevel;
import uz.topdim.identity.entity.User;

import static org.assertj.core.api.Assertions.assertThat;

class TrustServiceTest {
    private final TrustService svc = new TrustService();

    private User u(boolean phoneVerified, boolean paid) {
        return User.builder().phoneVerified(phoneVerified)
                .paidAt(paid ? java.time.LocalDateTime.now() : null).build();
    }

    @Test void l0_whenNoProof() { assertThat(svc.computeTrustLevel(u(false, false))).isEqualTo(TrustLevel.L0); }
    @Test void l1_whenPhoneVerified() { assertThat(svc.computeTrustLevel(u(true, false))).isEqualTo(TrustLevel.L1); }
    @Test void l1_whenPaid() { assertThat(svc.computeTrustLevel(u(false, true))).isEqualTo(TrustLevel.L1); }
}
