/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

-- Demo Users
INSERT INTO users (id, name, password)
VALUES (1, 'Alice Johnson', 'password1'),
       (2, 'Bob Smith', 'password2'),
       (3, 'Charlie Brown', 'password3');

-- Demo Contacts
INSERT INTO contacts (id, name, phone)
VALUES (1, 'Alice Johnson', '+1 555-1234'),
       (2, 'Bob Smith', '+1 555-5678'),
       (3, 'Charlie Brown', '+1 555-8765');

-- Demo Messages
INSERT INTO messages (id, sender_id, receiver_id, content)
VALUES (1, 1, 2, 'Hi Bob, this is Alice.'),
       (2, 2, 1, 'Hello Alice! How are you?'),
       (3, 1, 3, 'Hey Charlie, are we still meeting tomorrow?');
