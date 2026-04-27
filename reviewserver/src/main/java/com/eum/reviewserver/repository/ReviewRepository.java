package com.eum.reviewserver.repository;

import com.eum.reviewserver.entity.Review;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long>, ReviewRepositoryCustom {
    Optional<Review> findByIdAndDeletedAtIsNull(Long id);
}
