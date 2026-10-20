# CuciVM

Virtual machines inside an Android app, no root. Personal project.

- **Android guests**: Android 8.1 on the [Twoyi](https://github.com/twoyi/twoyi) container (MPL-2.0).
  CuciVM downloads Twoyi's official release APK at first use and takes the ROM and native libraries
  from it; nothing from Twoyi is stored in this repository. One Android VM runs at a time.
- **Linux guests**: Alpine and Ubuntu root filesystems from their official mirrors, run under PRoot,
  desktop shown through TigerVNC's Xvnc and CuciVM's own VNC client.
- **Windows / other OS**: planned (Box64 + Wine for Windows programs, QEMU for full systems; full
  systems are emulated, so they are slow: Android does not give apps hardware virtualisation).
- **macOS**: not supported. Apple's licence only allows macOS on Apple hardware.

## Catalogue

`catalog/catalog.json` lists the systems the app offers. The app reads it from this repository
(`raw.githubusercontent.com/Fr3nK027/CuciVM/main/catalog/catalog.json`) and falls back to the copy
built into the APK. Each entry: `id, os, name, version, arch, engine (proot | android_container | qemu),
url, mirrors[], sha256, size, logo, setup, session`. Files larger than 2 GB go in `parts[]`
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
| `io/twoyi/Renderer.java` (JNI declarations matching Twoyi's library) | MPL-2.0 | https://github.com/twoyi/twoyi |

The binaries are unmodified Termux packages (`packages.termux.dev`, aarch64).
