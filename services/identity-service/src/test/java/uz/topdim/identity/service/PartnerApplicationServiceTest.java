package uz.topdim.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.identity.dto.PartnerApplicationRequest;
import uz.topdim.identity.entity.ApplicationStatus;
import uz.topdim.identity.entity.PartnerApplication;
import uz.topdim.identity.repository.PartnerApplicationRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerApplicationServiceTest {

    @Mock
    private PartnerApplicationRepository repository;

    @InjectMocks
    private PartnerApplicationService service;

    @Test
    @DisplayName("submit: duplicate pending phone is rejected")
    void submit_duplicatePendingPhone_rejected() {
        PartnerApplicationRequest request = new PartnerApplicationRequest();
        request.setFirstName("Ali");
        request.setLastName("Valiev");
        request.setPhone("+998 90 123 45 67");
        request.setCompanyName("Ali Cafe");
        request.setCity("Tashkent");
        request.setAddress("Amir Temur 10");
        request.setBusinessCategory("Cafe");

        when(repository.existsByPhoneAndStatus("+998901234567", ApplicationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending partner application already exists");

        verify(repository, never()).save(any(PartnerApplication.class));
    }
}
