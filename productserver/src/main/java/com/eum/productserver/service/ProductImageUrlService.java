package com.eum.productserver.service;

import com.eum.s3.S3Component;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductImageUrlService {

    private final S3Component s3Component;

    public String toDisplayUrl(String imageUrlOrKey) {
        return s3Component.toPublicUrl(imageUrlOrKey);
    }
}
