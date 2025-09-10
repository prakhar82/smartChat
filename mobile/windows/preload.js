/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: Prakhar Dwivedi
 */

const { contextBridge } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
    ping: () => 'pong'
});
