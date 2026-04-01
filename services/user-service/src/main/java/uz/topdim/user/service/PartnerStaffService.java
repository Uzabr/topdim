package uz.topdim.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.user.dto.CreateStaffRequest;
import uz.topdim.user.dto.StaffResponse;
import uz.topdim.user.entity.Staff;
import uz.topdim.user.exception.ResourceNotFoundException;
import uz.topdim.user.repository.StaffRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Управление сотрудниками партнёра (например, кассирами).
 * Позволяет мерчанту добавлять, просматривать и удалять/деактивировать свой стафф.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerStaffService {

    private final StaffRepository staffRepository;

    @Transactional(readOnly = true)
    public List<StaffResponse> getMyStaff(Long userId) {
        return staffRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public StaffResponse addStaff(Long userId, CreateStaffRequest request) {
        Staff staff = Staff.builder()
                .userId(userId)
                .name(request.getName())
                .phone(request.getPhone())
                .role(request.getRole() != null ? request.getRole() : "CASHIER")
                .active(true)
                .build();

        staff = staffRepository.save(staff);
        log.info("PARTNER: userId={} добавил сотрудника {} ({})", userId, staff.getId(), staff.getPhone());
        
        return mapToResponse(staff);
    }

    @Transactional
    public void removeStaff(Long userId, Long staffId) {
        Staff staff = staffRepository.findByUserIdAndId(userId, staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Сотрудник не найден или не принадлежит вам"));
        
        // Физически удаляем (или можно сделать active = false)
        staffRepository.delete(staff);
        log.info("PARTNER: userId={} удалил сотрудника {}", userId, staffId);
    }

    private StaffResponse mapToResponse(Staff staff) {
        return StaffResponse.builder()
                .id(staff.getId())
                .name(staff.getName())
                .phone(staff.getPhone())
                .role(staff.getRole())
                .active(staff.isActive())
                .createdAt(staff.getCreatedAt())
                .build();
    }
}
