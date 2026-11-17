# ASTRAL grandMA3 Remote

Android application project for a phone/tablet remote-control interface for grandMA3.

## Current project contents

- Dark navy / blue ASTRAL interface.
- 15 executor tiles in a 3-column layout, with bank navigation.
- GO and OFF buttons per executor, plus a level slider.
- Global GO / PAUSE / OFF / FLASH controls.
- Configurable console IP, OSC UDP port, OSC message address, and Web Remote port.
- Embedded grandMA3 Web Remote screen.
- GitHub Actions workflow that builds a debug APK and uploads it as a workflow artifact.

## Build

Every push to `main` starts the Android build. You can also start it manually from **Actions → Android APK → Run workflow**.

When the workflow succeeds, open its run and download the artifact named **ASTRAL-grandMA3-Remote-debug**.

## Important integration note

The app currently sends OSC messages containing command strings to the configured OSC address (default `/cmd`) and UDP port (default `8000`). A successful UDP send only confirms that the packet left the phone; it does **not** confirm that grandMA3 accepted or executed the command. The OSC address, input port, command syntax, and executor mapping must be verified against the target grandMA3 2.4 show/network configuration before operating real equipment.

The Web Remote tab opens the configured URL (default port `8080`). The console and phone/tablet must be reachable on the same network or an appropriately routed network.

## Version

1.0.0 — initial source project. This is a development build, not yet a verified production controller.
