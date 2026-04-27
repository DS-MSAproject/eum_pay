package com.eum.reviewserver.repository;

import com.eum.reviewserver.entity.Review;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Review> findByIdAndDeletedAtIsNull(Long id);
    Optional<Review> findByPublicIdAndDeletedAtIsNull(UUID publicId);
    boolean existsByProductIdAndWriterId(Long productId, Long writerId);
}
