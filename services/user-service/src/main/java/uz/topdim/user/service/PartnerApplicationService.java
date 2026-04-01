package uz.topdim.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.user.dto.PartnerApplicationRequest;
import uz.topdim.user.dto.PartnerApplicationResponse;
import uz.topdim.user.entity.ApplicationStatus;
import uz.topdim.user.entity.PartnerApplication;
import uz.topdim.user.repository.PartnerApplicationRepository;

@Service
@RequiredArgsConstructor
public class PartnerApplicationService {

    private final PartnerApplicationRepository repository;

    /**
     * Публичный метод: партнёр подаёт заявку с лендинга.
     */
    @Transactional
    public PartnerApplicationResponse submit(PartnerApplicationRequest request) {
        PartnerApplication application = PartnerApplication.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .companyName(request.getCompanyName())
                .comment(request.getComment())
                .status(ApplicationStatus.PENDING)
                .build();

        PartnerApplication saved = repository.save(application);
        return toResponse(saved);
    }

    /**
     * Админский метод: получить список заявок с фильтром по статусу.
     */
    public Page<PartnerApplicationResponse> getAll(ApplicationStatus status, Pageable pageable) {
        Page<PartnerApplication> page;
        if (status != null) {
            page = repository.findByStatus(status, pageable);
        } else {
            page = repository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    /**
     * Админский метод: получить заявку по ID.
     */
    public PartnerApplicationResponse getById(Long id) {
        PartnerApplication app = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена: " + id));
        return toResponse(app);
    }

    /**
     * Админский метод: одобрить заявку. После этого админ вручную создаёт партнёра.
     */
    @Transactional
    public PartnerApplicationResponse approve(Long id) {
        PartnerApplication app = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена: " + id));
        app.setStatus(ApplicationStatus.APPROVED);
        return toResponse(repository.save(app));
    }

    /**
     * Админский метод: отклонить заявку.
     */
    @Transactional
    public PartnerApplicationResponse reject(Long id) {
        PartnerApplication app = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена: " + id));
        app.setStatus(ApplicationStatus.REJECTED);
        return toResponse(repository.save(app));
    }

    private PartnerApplicationResponse toResponse(PartnerApplication entity) {
        return PartnerApplicationResponse.builder()
                .id(entity.getId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .phone(entity.getPhone())
                .companyName(entity.getCompanyName())
                .comment(entity.getComment())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
