package com.retention.platform.customer_retention_platform.repository;

import com.retention.platform.customer_retention_platform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
}
