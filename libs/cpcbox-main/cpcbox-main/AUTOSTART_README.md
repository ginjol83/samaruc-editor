# CPCBox Auto-Start Feature

## Overview

The CPCBox emulator now includes an auto-start feature that automatically boots the emulator with a chosen configuration instead of waiting for user interaction.

## Configuration

Auto-start behavior is controlled by the `autostart-config.js` file. You can easily modify the following settings:

### Enable/Disable Auto-Start
```javascript
var AUTO_START_ENABLED = true;  // Set to false to disable auto-start
```

### Choose Default Configuration
```javascript
var AUTO_START_CONFIG = 'boot_cpc464';  // Default configuration to start with
```

### Set Startup Delay
```javascript
var AUTO_START_DELAY = 2000;  // Delay in milliseconds (2000 = 2 seconds)
```

### Customize Status Message
```javascript
var AUTO_START_MESSAGE = 'Auto-starting CPC 464...';  // Message shown during startup
```

## Available Configurations

### Boot Configurations
- `'boot_cpc464'` - CPC 464 with Basic
- `'boot_cpc664'` - CPC 664 with Basic  
- `'boot_cpc6128'` - CPC 6128 with Basic

### Popular Games
- `'1943.sna'` - 1943 (shooting game)
- `'arkanoid.sna'` - Arkanoid (breakout game)
- `'batman.sna'` - Batman (platformer)
- `'commando.sna'` - Commando (action game)
- `'cybernoid.sna'` - Cybernoid (shooting game)
- `'rick_dangerous.sna'` - Rick Dangerous (platformer)
- `'bruce_lee.sna'` - Bruce Lee (fighting game)
- `'ghosts_n_goblins.sna'` - Ghosts'n Goblins (platformer)

### Demos
- `'backtro.sna'` - Backtro demo
- `'dtc.sna'` - DTC demo
- `'ecole_buissonniere.sna'` - Ecole Buissonnière demo

## Usage Examples

### Start with CPC 464 Basic
```javascript
var AUTO_START_ENABLED = true;
var AUTO_START_CONFIG = 'boot_cpc464';
var AUTO_START_DELAY = 2000;
var AUTO_START_MESSAGE = 'Auto-starting CPC 464...';
```

### Start with a Game
```javascript
var AUTO_START_ENABLED = true;
var AUTO_START_CONFIG = 'arkanoid.sna';
var AUTO_START_DELAY = 3000;
var AUTO_START_MESSAGE = 'Loading Arkanoid...';
```

### Disable Auto-Start
```javascript
var AUTO_START_ENABLED = false;
```

## How It Works

1. When the page loads, the auto-start script checks if `AUTO_START_ENABLED` is true
2. If enabled, it sets the configuration dropdown to the chosen value
3. After the specified delay, it automatically triggers the emulator startup
4. The emulator boots with the selected configuration
5. Users can still manually change configurations using the dropdown

## Benefits

- **Immediate Access**: Users don't need to manually select a configuration
- **Customizable**: Easy to change which configuration starts by default
- **Flexible**: Can be disabled for users who prefer manual selection
- **User-Friendly**: Shows status messages during startup process

## Technical Details

- The auto-start script runs after the document is ready
- It uses jQuery to trigger the same change event that manual selection uses
- The delay ensures all emulator components are fully loaded before starting
- The feature is backward compatible - if the config file is missing, auto-start is disabled

## Troubleshooting

If auto-start isn't working:

1. Check that `autostart-config.js` is loaded correctly
2. Verify that `AUTO_START_ENABLED` is set to `true`
3. Ensure the `AUTO_START_CONFIG` value matches an available option
4. Check browser console for any JavaScript errors
5. Try increasing the `AUTO_START_DELAY` if the emulator isn't fully loaded
