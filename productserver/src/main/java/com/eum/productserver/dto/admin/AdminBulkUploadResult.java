package com.eum.productserver.dto.admin;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminBulkUploadResult {

    private int totalRows;
    private int successCount;
    private int failCount;
    private List<String> errors;
}
