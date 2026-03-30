package uz.topdim.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.user.dto.CreateStaffRequest;
import uz.topdim.user.dto.StaffResponse;
import uz.topdim.user.entity.Staff;
import uz.topdim.user.exception.ResourceNotFoundException;
import uz.topdim.user.repository.StaffRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerStaffServiceTest {

    @Mock
    private StaffRepository staffRepository;

    @InjectMocks
    private PartnerStaffService partnerStaffService;

    private Staff createStaff(Long id, Long userId) {
        return Staff.builder()
                .id(id)
                .userId(userId)
                .name("Test Cashier")
                .phone("998901234567")
                .role("CASHIER")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("getMyStaff: возвращает всех сотрудников партнёра")
    void getMyStaff_returnsStaffList() {
        when(staffRepository.findByUserId(10L)).thenReturn(List.of(createStaff(1L, 10L)));

        List<StaffResponse> result = partnerStaffService.getMyStaff(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Test Cashier");
        assertThat(result.get(0).getRole()).isEqualTo("CASHIER");
    }

    @Test
    @DisplayName("addStaff: успешно добавляет сотрудника")
    void addStaff_success() {
        CreateStaffRequest req = new CreateStaffRequest();
        req.setName("Ivan");
        req.setPhone("111");

        when(staffRepository.save(any(Staff.class))).thenAnswer(inv -> {
            Staff s = inv.getArgument(0);
            s.setId(100L);
            return s;
        });

        StaffResponse result = partnerStaffService.addStaff(10L, req);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getName()).isEqualTo("Ivan");
        assertThat(result.getPhone()).isEqualTo("111");
        assertThat(result.getRole()).isEqualTo("CASHIER"); // default
        verify(staffRepository, times(1)).save(any(Staff.class));
    }

    @Test
    @DisplayName("removeStaff: удаляет сотрудника, если он принадлежит партнёру")
    void removeStaff_success() {
        Staff staff = createStaff(1L, 10L);
        when(staffRepository.findByUserIdAndId(10L, 1L)).thenReturn(Optional.of(staff));

        partnerStaffService.removeStaff(10L, 1L);

        verify(staffRepository, times(1)).delete(staff);
    }

    @Test
    @DisplayName("removeStaff: чужой сотрудник выбрасывает ResourceNotFoundException")
    void removeStaff_otherPartner_throws() {
        when(staffRepository.findByUserIdAndId(10L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerStaffService.removeStaff(10L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Сотрудник не найден или не принадлежит вам");
    }
}
