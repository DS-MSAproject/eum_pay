package com.eum.authserver.repository;

import com.eum.authserver.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByPhoneNumber(String phoneNumber);
    long countByCreatedAtGreaterThanEqual(LocalDateTime since);

    @Query("""
        select u from User u
        where (:keyword is null or :keyword = ''
               or lower(u.email) like lower(concat('%', :keyword, '%'))
               or lower(u.name)  like lower(concat('%', :keyword, '%')))
        order by u.createdAt desc
        """)
    Page<User> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}