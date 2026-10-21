package com.sbertech.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StatusDto {
    private final Long id;
    private final String status;
}
