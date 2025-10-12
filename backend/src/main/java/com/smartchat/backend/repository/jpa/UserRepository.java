/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.backend.repository.jpa;

import com.smartchat.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<com.smartchat.backend.model.User> findByMobileNumber(String mobileNumber);

    Optional<com.smartchat.backend.model.User> findByEmail(String email);

    Optional<User> findByMobileNormalized(String mobileNormalized);

    List<User> findByMobileNormalizedIn(List<String> mobiles);

    boolean existsByMobileNormalized(String phoneNormalized);
}
