package com.eum.orderserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor  // JSON 파싱(Jackson)을 위해 필수!
@AllArgsConstructor // Builder 패턴 사용을 위해 필요
public class OrderRequest {

        @JsonProperty("user_id")
        private Long userId;

        @JsonProperty("user_name")
        private String userName;

        @JsonProperty("items")
        @NotEmpty
        private List<@Valid OrderItemRequest> items;

        @NotBlank
        @JsonProperty("receiver_name")
        private String receiverName;

        @NotBlank
        @JsonProperty("receiver_phone")
        private String receiverPhone;

        @NotBlank
        @JsonProperty("receiver_addr")
        private String receiverAddr;
}
