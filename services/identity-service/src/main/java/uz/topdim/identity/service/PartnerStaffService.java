package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.identity.dto.CreateStaffRequest;
import uz.topdim.identity.dto.PartnerStaffResponse;
import uz.topdim.identity.entity.Staff;
import uz.topdim.identity.exception.ResourceNotFoundException;
import uz.topdim.identity.repository.StaffRepository;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerStaffService {
    private final StaffRepository staffRepository;

    @Transactional(readOnly = true)
    public List<PartnerStaffResponse> getMyStaff(Long userId) {
        return staffRepository.findByUserId(userId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional
    public PartnerStaffResponse addStaff(Long userId, CreateStaffRequest request) {
        Staff staff = Staff.builder().userId(userId).name(request.getName())
                .phone(request.getPhone()).role(request.getRole() != null ? request.getRole() : "CASHIER")
                .active(true).build();
        staff = staffRepository.save(staff);
        log.info("PARTNER: userId={} добавил сотрудника {} ({})", userId, staff.getId(), staff.getPhone());
        return mapToResponse(staff);
    }

    @Transactional
    public void removeStaff(Long userId, Long staffId) {
        Staff staff = staffRepository.findByUserIdAndId(userId, staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Сотрудник не найден или не принадлежит вам"));
        staffRepository.delete(staff);
        log.info("PARTNER: userId={} удалил сотрудника {}", userId, staffId);
    }

    private PartnerStaffResponse mapToResponse(Staff staff) {
        return PartnerStaffResponse.builder().id(staff.getId()).name(staff.getName())
                .phone(staff.getPhone()).role(staff.getRole()).active(staff.isActive())
                .createdAt(staff.getCreatedAt()).build();
    }
}
