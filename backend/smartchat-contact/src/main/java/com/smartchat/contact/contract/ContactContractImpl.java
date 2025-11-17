/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

package com.smartchat.contact.contract;

import com.smartchat.common.contracts.ContactContract;
import com.smartchat.contact.service.GoogleContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactContractImpl implements ContactContract {

    GoogleContactService googleContactService;

    @Override
    public void fetchAndSync(Long ownerUserId, String accessToken) {
        googleContactService.fetchAndSync(ownerUserId, accessToken);
    }
}
