package com.eum.cartserver.service;

import com.eum.cartserver.client.ProductCatalogClient;
import com.eum.cartserver.client.dto.ProductFrontendDto;
import com.eum.cartserver.client.dto.ProductFrontendOptionDto;
import com.eum.cartserver.exception.InvalidCartRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductOptionPolicyServiceTest {

    @Mock
    private ProductCatalogClient productCatalogClient;

    @InjectMocks
    private ProductOptionPolicyService productOptionPolicyService;

    @Test
    void resolveOptionIdForAdd_returnsZeroWhenProductHasNoOptions() {
        when(productCatalogClient.getFrontendProduct(100L))
                .thenReturn(frontendProduct(100L, List.of()));

        Long optionId = productOptionPolicyService.resolveOptionIdForAdd(100L, null);

        assertThat(optionId).isEqualTo(0L);
    }

    @Test
    void resolveOptionIdForAdd_rejectsMissingOptionWhenProductHasOptions() {
        when(productCatalogClient.getFrontendProduct(100L))
                .thenReturn(frontendProduct(100L, List.of(option(200L), option(201L))));

        assertThatThrownBy(() -> productOptionPolicyService.resolveOptionIdForAdd(100L, null))
                .isInstanceOf(InvalidCartRequestException.class)
                .hasMessage("옵션이 있는 상품은 옵션을 선택해야 합니다.");
    }

    @Test
    void resolveOptionIdForAdd_rejectsNonMemberOption() {
        when(productCatalogClient.getFrontendProduct(100L))
                .thenReturn(frontendProduct(100L, List.of(option(200L))));

        assertThatThrownBy(() -> productOptionPolicyService.resolveOptionIdForAdd(100L, 201L))
                .isInstanceOf(InvalidCartRequestException.class)
                .hasMessageContaining("상품에 속하지 않은 옵션");
    }

    @Test
    void resolveOptionIdForChange_acceptsValidTargetOption() {
        when(productCatalogClient.getFrontendProduct(100L))
                .thenReturn(frontendProduct(100L, List.of(option(200L), option(201L))));

        Long optionId = productOptionPolicyService.resolveOptionIdForChange(100L, 200L, 201L);

        assertThat(optionId).isEqualTo(201L);
    }

    private ProductFrontendDto frontendProduct(Long productId, List<ProductFrontendOptionDto> options) {
        ProductFrontendDto dto = new ProductFrontendDto();
        ReflectionTestUtils.setField(dto, "productId", productId);
        ReflectionTestUtils.setField(dto, "options", options);
        return dto;
    }

    private ProductFrontendOptionDto option(Long optionId) {
        ProductFrontendOptionDto dto = new ProductFrontendOptionDto();
        ReflectionTestUtils.setField(dto, "optionId", optionId);
        ReflectionTestUtils.setField(dto, "optionName", "option-" + optionId);
        return dto;
    }
}
