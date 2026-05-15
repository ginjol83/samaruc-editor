# CPCBox Project Structure Documentation

## Overview

CPCBox is a JavaScript-based Amstrad CPC computer emulator that runs directly in web browsers. This document provides a comprehensive breakdown of all files and directories, explaining their purpose and how they work together to create a complete 8-bit computer emulation experience.

## Project Root Directory

```
/Users/chrisg/github/cpcbox/
├── index.html                    # Main application entry point
├── about.html                    # Project documentation and information
├── cpcbox.js                     # Core emulator engine (obfuscated)
├── cpcbox.sysroms.js            # System ROMs and firmware data
├── deploy.sh                     # Deployment script
├── css/                          # Stylesheets directory
├── libs/                         # External JavaScript libraries
├── img/                          # Images and graphics assets
├── disks/                        # Disk images for software
└── snapshots/                    # Game snapshots and demos
```

## Core Application Files

### `index.html`
**Purpose**: Main HTML interface and application entry point

**Key Components**:
- **HTML5 Canvas**: 768x544 pixel display for emulator output
- **Control Panel**: Dropdown for game/configuration selection
- **Settings Panel**: Hardware configuration options (brand, keyboard, CRTC, monitor, audio)
- **Drive Controls**: Interface for floppy disk drives A and B
- **Tape Deck**: Complete tape loading interface with play/record/rewind controls
- **Status Display**: Shows emulator state and performance metrics

**Features**:
- Responsive design with fullscreen support
- File input handling for user-provided disk/tape images
- Real-time status updates and FPS counter
- Keyboard and joystick input mapping

### `about.html`
**Purpose**: Comprehensive project documentation and user guide

**Content Sections**:
- **Amstrad CPC History**: Background on the original hardware
- **CPCBox Features**: Emulator capabilities and benefits
- **Usage Instructions**: How to load and run software
- **Release History**: Development timeline and milestones
- **Community Links**: Resources and related projects
- **Technical Requirements**: Browser compatibility information

**Styling**: Uses Bootstrap framework for responsive documentation layout

### `cpcbox.js`
**Purpose**: Core emulator engine (heavily obfuscated/minified)

**Estimated Components** (based on analysis):
- **Z80 CPU Emulation**: Complete processor implementation
- **Memory Management**: ROM/RAM handling and memory mapping
- **CRTC Emulation**: Video controller and display rendering
- **AY Sound Chip**: Audio synthesis and output
- **I/O Systems**: Keyboard, joystick, disk, and tape interfaces
- **Timing System**: Precise emulation timing and synchronization
- **File Format Support**: .sna, .dsk, .tap, .wav file handling

**Architecture**:
- Object-oriented design with modular components
- Event-driven execution model
- Canvas-based video rendering
- Web Audio API integration for sound

### `cpcbox.sysroms.js`
**Purpose**: Contains preloaded system ROMs and firmware

**ROM Contents**:
- **CPC464_Basic.rom**: Basic interpreter for CPC 464
- **CPC464_OS_English.rom**: English operating system
- **CPC464_OS_French.rom**: French operating system  
- **CPC464_OS_Spanish.rom**: Spanish operating system
- **CPC664_Basic.rom**: Basic interpreter for CPC 664
- **CPC664_OS.rom**: Operating system for CPC 664
- **CPC6128_Basic.rom**: Basic interpreter for CPC 6128
- **CPC6128_OS_English.rom**: English OS for CPC 6128
- **CPC6128_OS_French.rom**: French OS for CPC 6128

**Format**: Base64-encoded binary data for direct browser loading

### `deploy.sh`
**Purpose**: Simple deployment script for web hosting

**Function**: Uses rsync to upload project files to remote server
```bash
rsync -r * amstrad@server:/home/amstrad/htdocs/amstrad.retrogamecoders.com
```

## Directory Structure

### `css/` - Stylesheets
```
css/
├── cpcbox.style.css              # Main emulator styling
├── Guifx_v2_Transports.woff     # Custom icon font
└── volume-slider.css             # Audio volume control styling
```

#### `cpcbox.style.css`
**Purpose**: Complete visual styling for the emulator interface

**Key Style Sections**:
- **Button Styles**: `.button`, `.disabled-button`, `.toggled-button`
- **Screen Display**: `#screen`, `#logo`, `#screen-placeholder`
- **Control Panels**: `.control-panel`, `.option-panel`
- **Drive Interfaces**: `fieldset`, `.led`, filename displays
- **Debug Interface**: `.debugger-gui`, `.dasm-table` (disassembly)
- **Responsive Design**: Fullscreen support, mobile adaptations

**Features**:
- Retro-styled interface matching original CPC aesthetics
- Interactive button states and hover effects
- Custom icon font integration
- Canvas display styling with shadow effects

#### `volume-slider.css`
**Purpose**: Specialized styling for audio volume controls

### `libs/` - External Dependencies
```
libs/
├── jquery-1.8.3.min.js          # jQuery library (DOM manipulation)
├── jquerytools.rangeinput.js    # Range input widget
└── inflate.js                   # Data decompression library
```

#### `jquery-1.8.3.min.js`
**Purpose**: jQuery library for DOM manipulation and event handling
**Usage**: Enables dynamic UI updates and user interaction handling

#### `jquerytools.rangeinput.js`
**Purpose**: Enhanced range input widget for sliders
**Features**: 
- Cross-browser compatibility for HTML5 range inputs
- Custom styling and behavior
- Volume control implementation

#### `inflate.js`
**Purpose**: JavaScript implementation of DEFLATE decompression
**Source**: Extracted from Mozilla's PDF.js project
**Usage**: Decompresses ZIP archives and compressed data files

### `img/` - Graphics Assets
```
img/
├── cpc464_logo.png              # CPC 464 computer logo
├── cpcbox-sprites.png           # UI button and control sprites
├── flag_uk.png                  # UK flag for English keyboard
├── Open_16x16.png              # File open icon
└── volume-slider.png           # Volume control graphics
```

**Purpose**: All visual assets for the emulator interface
- **Logo**: Branded display when emulator is idle
- **Sprites**: Icon set for buttons and controls
- **Flags**: Localization indicators for keyboard layouts
- **Icons**: Standard UI elements

### `disks/` - Disk Images
```
disks/
├── blank.dsk                    # Empty disk image
├── cpmplus1.dsk                 # CP/M Plus disk 1
├── cpmplus2.dsk                 # CP/M Plus disk 2
├── cpmplus3.dsk                 # CP/M Plus disk 3
├── cpmplus4.dsk                 # CP/M Plus disk 4
├── hitech-c.dsk                 # Hi-Tech C compiler
├── mbasic.dsk                   # Microsoft Basic
└── rogue.dsk                    # Rogue game
```

**Purpose**: Additional software and utilities
- **System Software**: Operating systems and development tools
- **Programming Languages**: Compilers and interpreters
- **Games**: Classic software titles
- **Blank Media**: For user-created content

### `snapshots/` - Game Library
```
snapshots/
├── [Game Files]                 # 50+ game snapshots (.sna)
├── [Demo Files]                 # Demo scene productions
├── [Disk Images]                # Additional .dsk files
├── [Tape Images]                # .tap tape files
└── [Screenshots]                # .jpg preview images
```

**Content Categories**:
- **Games**: 1943, Arkanoid, Batman, Bruce Lee, Commando, etc.
- **Demos**: Backtro, DTC, Ecole Buissonnière, etc.
- **System Software**: CP/M, various OS versions
- **Media**: Screenshots and preview images

**File Types**:
- **`.sna`**: Snapshot files (saved game states)
- **`.dsk`**: Disk images
- **`.tap`**: Tape images
- **`.jpg`**: Screenshot previews

## How It All Works Together

### 1. **Initialization Process**
1. Browser loads `index.html`
2. CSS files style the interface
3. JavaScript libraries load (jQuery, inflate.js)
4. System ROMs load from `cpcbox.sysroms.js`
5. Main emulator engine initializes from `cpcbox.js`

### 2. **User Interaction Flow**
1. User selects configuration from dropdown
2. Settings panel allows hardware customization
3. File inputs enable loading custom disk/tape images
4. Control buttons manage emulator execution
5. Canvas displays real-time emulator output

### 3. **Emulation Execution**
1. Z80 CPU emulation runs in timing loop
2. Memory management handles ROM/RAM access
3. CRTC generates video output to Canvas
4. AY chip synthesizes audio output
5. I/O systems handle user input and file operations

### 4. **File Handling**
1. User selects disk/tape files via file input
2. `inflate.js` decompresses ZIP archives if needed
3. Emulator loads file data into appropriate memory locations
4. File system emulation provides access to disk contents

### 5. **Rendering Pipeline**
1. Emulator generates pixel data
2. Canvas context draws pixels to screen
3. CSS styling provides visual effects and layout
4. Status updates show performance metrics

## Technical Architecture

### **Frontend Stack**
- **HTML5**: Canvas for video, File API for loading
- **CSS3**: Modern styling with gradients and animations
- **JavaScript**: Core emulation logic and UI interaction
- **jQuery**: DOM manipulation and event handling

### **Emulation Components**
- **CPU**: Z80 processor emulation
- **Memory**: ROM/RAM management system
- **Video**: CRTC controller and display rendering
- **Audio**: AY sound chip synthesis
- **Storage**: Disk and tape emulation
- **Input**: Keyboard and joystick handling

### **Browser Integration**
- **Canvas API**: Hardware-accelerated graphics rendering
- **Web Audio API**: Sound synthesis and playback
- **File API**: Local file loading and processing
- **Local Storage**: Settings persistence
- **Fullscreen API**: Immersive display mode

## Performance Considerations

### **Optimization Strategies**
- **Timing Loops**: Precise emulation timing with `setInterval`
- **Canvas Rendering**: Efficient pixel manipulation
- **Memory Management**: Optimized ROM/RAM access patterns
- **Audio Buffering**: Smooth sound output without glitches

### **Browser Compatibility**
- **Modern Browsers**: Full feature support
- **Legacy Support**: Graceful degradation for older browsers
- **Mobile Devices**: Touch-friendly interface adaptations
- **Audio Limitations**: Deprecated API warnings for sound

## Development and Deployment

### **Build Process**
- **No Build Step**: Direct browser execution
- **Minification**: Core emulator is obfuscated
- **Asset Optimization**: Compressed images and fonts
- **Deployment**: Simple rsync-based upload

### **Maintenance**
- **ROM Updates**: System firmware can be updated
- **Game Library**: New snapshots can be added
- **Bug Fixes**: Core emulator improvements
- **Feature Additions**: New hardware support

This architecture creates a complete, self-contained Amstrad CPC emulator that runs entirely in web browsers, providing an authentic 8-bit computing experience with modern web technologies.
