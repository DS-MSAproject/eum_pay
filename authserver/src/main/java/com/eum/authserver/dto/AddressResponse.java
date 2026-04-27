package com.eum.authserver.dto;

import com.eum.authserver.entity.Address;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AddressResponse {

    private String status;
    private Data data;

    @Getter
    @Builder
    public static class Data {
        private int totalCount;
        private List<AddressItem> addresses;
    }

    @Getter
    @Builder
    public static class AddressItem {
        private Long addressId;
        private String addressName;
        private boolean isDefault;
        private String recipientName;
        private String postcode;
        private String baseAddress;
        private String detailAddress;
        private String extraAddress;
        private String addressType;
        private String phoneNumber;
        private LocalDateTime updatedAt;

        public static AddressItem from(Address address) {
            return AddressItem.builder()
                    .addressId(address.getId())
                    .addressName(address.getAddressName())
                    .isDefault(address.isDefault())
                    .recipientName(address.getRecipientName())
                    .postcode(address.getZipCode())
                    .baseAddress(address.getBaseAddress())
                    .detailAddress(address.getDetailAddress())
                    .extraAddress(address.getExtraAddress())
                    .addressType(address.getAddressType())
                    .phoneNumber(address.getPhoneNumber())
                    .updatedAt(address.getUpdatedAt())
                    .build();
        }
    }
}
