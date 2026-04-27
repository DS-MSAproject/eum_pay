package com.eum.cartserver.service;

import com.eum.cartserver.client.ProductCatalogClient;
import com.eum.cartserver.client.dto.ProductFrontendDto;
import com.eum.cartserver.client.dto.ProductFrontendOptionDto;
import com.eum.cartserver.exception.InvalidCartRequestException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductOptionPolicyService {

    public static final long NO_OPTION_ID = 0L;

    private final ProductCatalogClient productCatalogClient;

    public Long resolveOptionIdForAdd(Long productId, Long requestedOptionId) {
        long normalizedOptionId = normalizeOptionId(requestedOptionId);
        ProductFrontendDto product = loadProduct(productId);
        List<ProductFrontendOptionDto> options = product.getOptions() != null ? product.getOptions() : List.of();

        if (options.isEmpty()) {
            if (normalizedOptionId != NO_OPTION_ID) {
                throw new InvalidCartRequestException("옵션이 없는 상품은 옵션을 선택할 수 없습니다.");
            }
            return NO_OPTION_ID;
        }

        if (normalizedOptionId == NO_OPTION_ID) {
            throw new InvalidCartRequestException("옵션이 있는 상품은 옵션을 선택해야 합니다.");
        }

        validateOptionBelongsToProduct(productId, normalizedOptionId, options);
        return normalizedOptionId;
    }

    public Long resolveOptionIdForChange(Long productId, Long currentOptionId, Long newOptionId) {
        long normalizedCurrentOptionId = normalizeOptionId(currentOptionId);
        long normalizedNewOptionId = normalizeOptionId(newOptionId);
        ProductFrontendDto product = loadProduct(productId);
        List<ProductFrontendOptionDto> options = product.getOptions() != null ? product.getOptions() : List.of();

        if (options.isEmpty()) {
            if (normalizedCurrentOptionId != NO_OPTION_ID || normalizedNewOptionId != NO_OPTION_ID) {
                throw new InvalidCartRequestException("옵션이 없는 상품은 옵션 변경을 할 수 없습니다.");
            }
            return NO_OPTION_ID;
        }

        if (normalizedCurrentOptionId == NO_OPTION_ID || normalizedNewOptionId == NO_OPTION_ID) {
            throw new InvalidCartRequestException("옵션이 있는 상품은 옵션을 선택해야 합니다.");
        }

        validateOptionBelongsToProduct(productId, normalizedCurrentOptionId, options);
        validateOptionBelongsToProduct(productId, normalizedNewOptionId, options);
        return normalizedNewOptionId;
    }

    public Long normalizeOptionId(Long optionId) {
        long normalizedOptionId = optionId == null ? NO_OPTION_ID : optionId;
        if (normalizedOptionId < NO_OPTION_ID) {
            throw new InvalidCartRequestException("optionId는 0 이상이어야 합니다.");
        }
        return normalizedOptionId;
    }

    private ProductFrontendDto loadProduct(Long productId) {
        try {
            return productCatalogClient.getFrontendProduct(productId);
        } catch (FeignException ex) {
            throw new InvalidCartRequestException("상품 정보를 확인할 수 없습니다. productId=" + productId);
        }
    }

    private void validateOptionBelongsToProduct(Long productId, Long optionId, List<ProductFrontendOptionDto> options) {
        boolean exists = options.stream()
                .map(ProductFrontendOptionDto::getOptionId)
                .anyMatch(optionId::equals);

        if (!exists) {
            throw new InvalidCartRequestException(
                    "상품에 속하지 않은 옵션입니다. productId=" + productId + ", optionId=" + optionId
            );
        }
    }
}
