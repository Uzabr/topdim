package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.CategoryResponse;
import uz.topdim.coupon.dto.CreateMerchantRequest;
import uz.topdim.coupon.dto.MerchantResponse;
import uz.topdim.coupon.entity.Merchant;
import uz.topdim.coupon.exception.ResourceNotFoundException;
import uz.topdim.coupon.repository.CategoryRepository;
import uz.topdim.coupon.repository.MerchantRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final CategoryRepository categoryRepository;

    // ==================== Merchants ====================

    @Transactional(readOnly = true)
    public List<MerchantResponse> getAllMerchants() {
        return merchantRepository.findByActiveTrue().stream()
                .map(this::mapMerchant)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchantById(Long id) {
        return mapMerchant(merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Партнёр не найден")));
    }

    @CacheEvict(value = "catalog", allEntries = true)
    @Transactional
    public MerchantResponse createMerchant(CreateMerchantRequest request) {
        Merchant merchant = Merchant.builder()
                .name(request.getName())
                .description(request.getDescription())
                .logoUrl(request.getLogoUrl())
                .coverUrl(request.getCoverUrl())
                .address(request.getAddress())
                .phone(request.getPhone())
                .email(request.getEmail())
                .website(request.getWebsite())
                .workingHours(request.getWorkingHours())
                .contactPerson(request.getContactPerson())
                .active(true)
                .build();
        return mapMerchant(merchantRepository.save(merchant));
    }

    @CacheEvict(value = "catalog", allEntries = true)
    @Transactional
    public MerchantResponse updateMerchant(Long id, CreateMerchantRequest request) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Партнёр не найден"));
        merchant.setName(request.getName());
        merchant.setDescription(request.getDescription());
        merchant.setLogoUrl(request.getLogoUrl());
        merchant.setCoverUrl(request.getCoverUrl());
        merchant.setAddress(request.getAddress());
        merchant.setPhone(request.getPhone());
        merchant.setEmail(request.getEmail());
        merchant.setWebsite(request.getWebsite());
        merchant.setWorkingHours(request.getWorkingHours());
        merchant.setContactPerson(request.getContactPerson());
        return mapMerchant(merchantRepository.save(merchant));
    }

    // ==================== Categories ====================

    @Cacheable(value = "categories")
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findByActiveTrueOrderBySortOrder().stream()
                .map(cat -> CategoryResponse.builder()
                        .id(cat.getId())
                        .name(cat.getName())
                        .nameUz(cat.getNameUz())
                        .slug(cat.getSlug())
                        .iconUrl(cat.getIconUrl())
                        .sortOrder(cat.getSortOrder())
                        .build())
                .collect(Collectors.toList());
    }

    private MerchantResponse mapMerchant(Merchant merchant) {
        return MerchantResponse.builder()
                .id(merchant.getId())
                .name(merchant.getName())
                .description(merchant.getDescription())
                .logoUrl(merchant.getLogoUrl())
                .coverUrl(merchant.getCoverUrl())
                .address(merchant.getAddress())
                .phone(merchant.getPhone())
                .email(merchant.getEmail())
                .website(merchant.getWebsite())
                .workingHours(merchant.getWorkingHours())
                .contactPerson(merchant.getContactPerson())
                .active(merchant.isActive())
                .build();
    }
}
