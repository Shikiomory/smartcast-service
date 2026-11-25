package com.sbertech.core.dto;

import java.util.List;

public record PlaylistDto(Long id, String name, List<MaterialDto> items) {
}
