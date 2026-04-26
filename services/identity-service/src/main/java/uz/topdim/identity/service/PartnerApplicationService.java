package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.identity.dto.PartnerApplicationRequest;
import uz.topdim.identity.dto.PartnerApplicationResponse;
import uz.topdim.identity.entity.ApplicationStatus;
import uz.topdim.identity.entity.PartnerApplication;
import uz.topdim.identity.repository.PartnerApplicationRepository;

@Service
@RequiredArgsConstructor
public class PartnerApplicationService {

    private final PartnerApplicationRepository repository;

    @Transactional
    public PartnerApplicationResponse submit(PartnerApplicationRequest request) {
        String phone = normalizePhone(request.getPhone());
        if (repository.existsByPhoneAndStatus(phone, ApplicationStatus.PENDING)) {
            throw new IllegalStateException("pending partner application already exists for phone");
        }

        PartnerApplication app = PartnerApplication.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phone(phone)
                .email(blankToNull(request.getEmail()))
                .companyName(request.getCompanyName().trim())
                .city(blankToNull(request.getCity()))
                .address(blankToNull(request.getAddress()))
                .workingHours(blankToNull(request.getWorkingHours()))
                .businessCategory(blankToNull(request.getBusinessCategory()))
                .website(blankToNull(request.getWebsite()))
                .telegramUsername(blankToNull(request.getTelegramUsername()))
                .comment(blankToNull(request.getComment()))
                .source("WEB")
                .status(ApplicationStatus.PENDING)
                .build();
        return toResponse(repository.save(app));
    }

    public Page<PartnerApplicationResponse> getAll(ApplicationStatus status, Pageable pageable) {
        Page<PartnerApplication> page = status != null
                ? repository.findByStatus(status, pageable)
                : repository.findAll(pageable);
        return page.map(this::toResponse);
    }

    public PartnerApplicationResponse getById(Long id) {
        return toResponse(repository.findById(id).orElseThrow(() -> new RuntimeException("Заявка не найдена: " + id)));
    }

    @Transactional
    public PartnerApplicationResponse approve(Long id) {
        PartnerApplication app = repository.findById(id).orElseThrow(() -> new RuntimeException("Заявка не найдена: " + id));
        app.setStatus(ApplicationStatus.APPROVED);
        return toResponse(repository.save(app));
    }

    @Transactional
    public PartnerApplicationResponse reject(Long id) {
        PartnerApplication app = repository.findById(id).orElseThrow(() -> new RuntimeException("Заявка не найдена: " + id));
        app.setStatus(ApplicationStatus.REJECTED);
        return toResponse(repository.save(app));
    }

    PartnerApplicationResponse toResponse(PartnerApplication e) {
        return PartnerApplicationResponse.builder()
                .id(e.getId()).firstName(e.getFirstName()).lastName(e.getLastName())
                .phone(e.getPhone()).email(e.getEmail()).companyName(e.getCompanyName())
                .city(e.getCity()).address(e.getAddress()).workingHours(e.getWorkingHours())
                .businessCategory(e.getBusinessCategory()).website(e.getWebsite())
                .telegramUsername(e.getTelegramUsername()).comment(e.getComment())
                .source(e.getSource()).status(e.getStatus())
                .rejectionReason(e.getRejectionReason()).reviewedBy(e.getReviewedBy())
                .reviewedAt(e.getReviewedAt()).linkedUserId(e.getLinkedUserId())
                .linkedMerchantId(e.getLinkedMerchantId())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).build();
    }

    String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("998")) {
            return "+" + digits;
        }
        return digits.isBlank() ? phone.trim() : "+" + digits;
    }
}
