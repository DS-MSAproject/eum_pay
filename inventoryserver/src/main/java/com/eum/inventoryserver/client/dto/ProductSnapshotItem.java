package com.eum.inventoryserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSnapshotItem {
    private Long productId;
    private String productName;
    private List<OptionSnapshotItem> options;
}
