package com.eum.reviewserver.repository;

import com.eum.reviewserver.entity.Review;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewRepositoryCustom {
    Page<Review> findPageByCondition(
            Long productId,
            String keyword,
            String sortType,
            String reviewType,
            Pageable pageable
    );

    List<Review> findAllByProductId(Long productId);

    boolean existsByProductIdAndWriterId(Long productId, Long writerId);
}
