package com.eum.productserver.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * productId cursor 기반 상품 스냅샷 bootstrap 페이지 응답입니다.
 */
@Getter
@Builder
public class ProductSnapshotBootstrapPageDto {

    private List<ProductSnapshotBootstrapDto> items;
    private Long nextLastProductId;
    private boolean hasNext;
}
