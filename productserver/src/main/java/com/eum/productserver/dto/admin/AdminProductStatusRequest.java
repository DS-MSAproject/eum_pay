package com.eum.productserver.dto.admin;

import com.eum.productserver.entity.ProductLifecycleStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminProductStatusRequest {

    @NotNull
    private ProductLifecycleStatus targetStatus;

    private String reason;
}
