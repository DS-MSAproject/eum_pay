package com.eum.authserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AddressRequest {

    private String addressName;  // 없으면 "미지정"으로 저장

    @NotBlank(message = "우편번호는 필수입니다.")
    @Pattern(regexp = "^\\d{5}$", message = "우편번호는 5자리 숫자여야 합니다.")
    private String postcode;

    @NotBlank(message = "기본 주소는 필수입니다.")
    @Size(max = 255, message = "기본 주소는 255자 이하여야 합니다.")
    private String baseAddress;

    @Size(max = 255, message = "상세 주소는 255자 이하여야 합니다.")
    private String detailAddress;

    @Size(max = 255, message = "추가 주소는 255자 이하여야 합니다.")
    private String extraAddress;

    @Pattern(regexp = "^(ROAD|JIBUN)$", message = "주소 유형은 ROAD 또는 JIBUN 이어야 합니다.")
    private String addressType;

    private boolean isDefault = false;
}
