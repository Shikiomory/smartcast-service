package com.sbertech.core.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MaterialDetailsDto extends MaterialDto {
    private String audioUrl;
    private String audioContentType;
    private Long audioSizeBytes;
}
