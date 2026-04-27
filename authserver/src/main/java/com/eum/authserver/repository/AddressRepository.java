package com.eum.authserver.repository;

import com.eum.authserver.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);
    Optional<Address> findByIdAndUserId(Long id, Long userId);
    boolean existsByIdAndUserId(Long id, Long userId);
    Optional<Address> findByUserIdAndIsDefaultTrue(Long userId);
    int countByUserId(Long userId);

    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.userId = :userId")
    void clearDefaultByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Address a WHERE a.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
