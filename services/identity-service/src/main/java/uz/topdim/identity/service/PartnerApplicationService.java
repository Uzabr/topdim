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
        PartnerApplication app = PartnerApplication.builder()
                .firstName(request.getFirstName()).lastName(request.getLastName())
                .phone(request.getPhone()).companyName(request.getCompanyName())
                .comment(request.getComment()).status(ApplicationStatus.PENDING).build();
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

    private PartnerApplicationResponse toResponse(PartnerApplication e) {
        return PartnerApplicationResponse.builder()
                .id(e.getId()).firstName(e.getFirstName()).lastName(e.getLastName())
                .phone(e.getPhone()).companyName(e.getCompanyName()).comment(e.getComment())
                .status(e.getStatus()).createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt()).build();
    }
}
