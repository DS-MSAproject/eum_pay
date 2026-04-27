package com.eum.searchserver.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "dseum.search")
public class SearchCategoryProperties {

    private List<Category> categories = new ArrayList<>();

    @Getter
    @Setter
    public static class Category {
        private String id;
        private String label;
        private List<SubCategory> subCategories = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class SubCategory {
        private Long id;
        private String code;
        private String label;
    }
}
