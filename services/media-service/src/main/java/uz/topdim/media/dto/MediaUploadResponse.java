package uz.topdim.media.dto;

import java.util.Map;

public record MediaUploadResponse(String id, String url, Map<String, String> variants) {
}
