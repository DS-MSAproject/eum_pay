package com.eum.reviewserver.repository;

import com.eum.reviewserver.entity.ReviewHelpful;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewHelpfulRepository extends JpaRepository<ReviewHelpful, Long> {
    boolean existsByReviewIdAndUserId(Long reviewId, Long userId);
}
