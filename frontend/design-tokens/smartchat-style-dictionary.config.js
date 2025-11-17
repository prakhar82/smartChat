/*
 * Copyright (c) 2025 SmartChat Contributors
 * All rights reserved.
 * Unauthorized copying or distribution of this file,
 * via any medium, is strictly prohibited unless permitted by license.
 * Author: $USER_NAME
 */

/**
 * =========================================================
 *  🧱 SmartChat Style Dictionary Configuration
 * ---------------------------------------------------------
 *  Converts SmartChat design tokens into:
 *   ✅ CSS Variables
 *   ✅ SCSS Variables
 *   ✅ Android Colors XML
 *   ✅ iOS Colors JSON
 * =========================================================
 */

const StyleDictionary = require('style-dictionary');

console.log('\n🎨 Building SmartChat Design Tokens...');

/**
 * ---------------------------------------------------------
 *  1️⃣ Custom Format: CSS Variables
 * ---------------------------------------------------------
 */
StyleDictionary.registerFormat({
  name: 'css/variables',
  formatter: ({dictionary}) => {
    return `/*! SmartChat CSS Variables - Auto-generated */
:root {
${dictionary.allProperties
      .map(
        (prop) => `  --${prop.name.replace(/\./g, '-')}: ${prop.value};`
      )
      .join('\n')}
}\n`;
  },
});

/**
 * ---------------------------------------------------------
 *  2️⃣ Custom Format: SCSS Variables
 * ---------------------------------------------------------
 */
StyleDictionary.registerFormat({
  name: 'scss/variables',
  formatter: ({dictionary}) => {
    return `// SmartChat SCSS Variables - Auto-generated
${dictionary.allProperties
      .map(
        (prop) => `$${prop.name.replace(/\./g, '-')}: ${prop.value};`
      )
      .join('\n')}
`;
  },
});

/**
 * ---------------------------------------------------------
 *  3️⃣ Custom Transform: Color Tokens to RGB/HEX
 * ---------------------------------------------------------
 */
StyleDictionary.registerTransform({
  name: 'color/hexOrRgba',
  type: 'value',
  matcher: (prop) => prop.attributes.category === 'color',
  transformer: (prop) => prop.value,
});

/**
 * ---------------------------------------------------------
 *  4️⃣ Build Configuration
 * ---------------------------------------------------------
 */
module.exports = {
  source: ['design-tokens/smartchat-tokens.json'],

  platforms: {
    css: {
      transformGroup: 'css',
      buildPath: 'dist/tokens/css/',
      files: [
        {
          destination: 'smartchat-tokens.css',
          format: 'css/variables',
        },
      ],
    },

    scss: {
      transformGroup: 'scss',
      buildPath: 'dist/tokens/scss/',
      files: [
        {
          destination: '_smartchat-tokens.scss',
          format: 'scss/variables',
        },
      ],
    },

    android: {
      transformGroup: 'android',
      buildPath: 'dist/tokens/android/',
      files: [
        {
          destination: 'smartchat_colors.xml',
          format: 'android/resources',
        },
      ],
    },

    ios: {
      transformGroup: 'ios',
      buildPath: 'dist/tokens/ios/',
      files: [
        {
          destination: 'SmartChatColors.json',
          format: 'json/nested',
        },
      ],
    },
  },
};
