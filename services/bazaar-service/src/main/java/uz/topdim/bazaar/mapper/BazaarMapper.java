package uz.topdim.bazaar.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import uz.topdim.bazaar.dto.BazaarResponse;
import uz.topdim.bazaar.dto.ShopResponse;
import uz.topdim.bazaar.entity.Bazaar;
import uz.topdim.bazaar.entity.Shop;
import uz.topdim.bazaar.entity.ShopProductTag;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface BazaarMapper {

    @Mapping(target = "shopCount", expression = "java(bazaar.getShops() != null ? bazaar.getShops().size() : 0)")
    BazaarResponse toResponse(Bazaar bazaar);

    @Mapping(target = "bazaarId", source = "bazaar.id")
    @Mapping(target = "bazaarName", source = "bazaar.name")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "productTags", source = "productTags", qualifiedByName = "tagsToStrings")
    ShopResponse toResponse(Shop shop);

    @Named("tagsToStrings")
    default List<String> tagsToStrings(List<ShopProductTag> tags) {
        if (tags == null) return List.of();
        return tags.stream().map(ShopProductTag::getTag).collect(Collectors.toList());
    }
}
