# CuciVM

Virtual machines inside an Android app, no root. Personal project.

- **Android spaces**: isolated spaces where apps from the phone (or APK files) run as separate
  copies, each with its own accounts and data, at native speed. Engine: NewBlackbox (Apache-2.0),
  built into the app, in `blackbox/`. Its crash-log upload to a third-party server is removed.
  Tested on Android 16: YouTube (video and sound), Chrome, Samsung Internet, Instagram.
  Apps that need Google services get them automatically: the space uses the ones already on the
  phone (nothing from Google is shipped with CuciVM).
- **Linux desktops**: Alpine, Debian 13 (XFCE + Firefox), Ubuntu 24.04 from official mirrors, run under
  PRoot at native arm64 speed, shown through TigerVNC's Xvnc and CuciVM's own VNC client. Laptop-style
  resolutions (landscape 720p/900p/1080p), mouse/touchpad buttons and wheel, hardware keyboard shortcuts,
  guest sound (PulseAudio), clipboard, shared Download folder, picture-in-picture, backup/restore.
- **Windows programs**: Wine 10.12 + Box64 0.4.5 on a Debian desktop (Notepad, Explorer). `.exe` and `.msi`
  files open with a tap; with sharing on, the phone's Download folder is an icon on the desktop.
- **Full PCs (slow)**: ReactOS, or Windows from your own ISO, emulated by QEMU. Android does not give
  apps hardware virtualisation, so these run on CPU emulation.
- **macOS**: not supported. Apple's licence only allows macOS on Apple hardware.

## Catalogue

`catalog/catalog.json` lists the systems the app offers. The app reads it from this repository
(`raw.githubusercontent.com/Fr3nK027/CuciVM/main/catalog/catalog.json`) and falls back to the copy
built into the APK. Each entry: `id, os, name, version, arch, engine (proot | app_space | qemu),
url, mirrors[], sha256, size, logo, setup, session`. `app_space` entries need no download. Files larger than 2 GB go in `parts[]`
(`{url, size, sha256}`, joined in order). Every entry needs a `sha256`; `CatalogueTest` checks the file.

Logos are links to their sources (Wikimedia Commons), not copies. They identify each system and stay
the property of their owners.

## Build

JDK 17, Android SDK (platform 35) and NDK 29.0.13846066 (for the app-space engine). `./gradlew assembleDebug`.
Sideload only: `targetSdk` is 25, so guest binaries can run from app storage and Wine keeps the SELinux
`execmod` permission it needs.

Debug builds include `DebugReceiver` (adb-driven VM control for automated tests); release builds do not.

## Third-party code in this repository

| part | licence | source |
| --- | --- | --- |
| PRoot (`app/src/main/assets/engine/proot.bin`, `loader.bin`), Termux build 5.1.107 | GPL-2.0 | https://github.com/termux/proot |
| libtalloc 2.5.0 (`libtalloc.so.2.bin`) | LGPL-3.0 | https://talloc.samba.org |
| libandroid-shmem 0.7 (`libandroid-shmem.so.bin`) | BSD-3-Clause | https://github.com/termux/libandroid-shmem |
| `blackbox/` (Bcore, black-reflection, compiler): NewBlackbox at the commit in `blackbox/UPSTREAM`; changed: `LogSender` and `ClientConfiguration.getLogSenderChatId` (no log upload), Java 17 target, MediaRouter package fix, new `ILocaleManagerProxy` | Apache-2.0 | https://github.com/ALEX5402/NewBlackbox |

The binaries are unmodified Termux packages (`packages.termux.dev`, aarch64).
