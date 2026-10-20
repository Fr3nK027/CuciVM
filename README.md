# CuciVM

Virtual machines inside an Android app, no root. Personal project.

- **Android spaces**: isolated spaces where apps from the phone (or APK files) run as separate
  copies, each with its own accounts and data, at native speed. Engine: NewBlackbox (Apache-2.0),
  built into the app, in `blackbox/`. Its crash-log upload to a third-party server is removed.
- **Linux guests**: Alpine and Ubuntu root filesystems from their official mirrors, run under PRoot,
  desktop shown through TigerVNC's Xvnc and CuciVM's own VNC client.
- **Windows / other OS**: planned (Box64 + Wine for Windows programs, QEMU for full systems; full
  systems are emulated, so they are slow: Android does not give apps hardware virtualisation).
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

JDK 17 and the Android SDK (platform 35). `./gradlew assembleDebug`. Sideload only: `targetSdk` is 28
so guest binaries can run from app storage.

## Third-party code in this repository

| part | licence | source |
| --- | --- | --- |
| PRoot (`app/src/main/assets/engine/proot.bin`, `loader.bin`), Termux build 5.1.107 | GPL-2.0 | https://github.com/termux/proot |
| libtalloc 2.5.0 (`libtalloc.so.2.bin`) | LGPL-3.0 | https://talloc.samba.org |
| libandroid-shmem 0.7 (`libandroid-shmem.so.bin`) | BSD-3-Clause | https://github.com/termux/libandroid-shmem |
| `blackbox/` (Bcore, black-reflection, compiler): NewBlackbox at the commit in `blackbox/UPSTREAM`; changed: `LogSender` and `ClientConfiguration.getLogSenderChatId` (no log upload), Java 17 target | Apache-2.0 | https://github.com/ALEX5402/NewBlackbox |

The binaries are unmodified Termux packages (`packages.termux.dev`, aarch64).
