package uz.topdim.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uz.topdim.user.dto.UserProfileResponse;

/**
 * MapStruct маппер для user-service.
 * Маппинг пользовательских DTO.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    // User entity lives in auth-service; this mapper is for when
    // user-service has its own profile-related mappings.
    // For now, it serves as a placeholder ready for use.
}
