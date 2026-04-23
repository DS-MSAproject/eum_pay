package com.eum.s3;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum S3Directory {
    PROFILE("auth/profiles"),
    PRODUCT("product/images"),
    BANNER("product/banner-images"),
    BRAND_STORY("product/story-images"),
    REVIEW("review/images"),
    BOARD("board/attachments");

    private final String path;
}
