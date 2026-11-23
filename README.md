# Formica

**English** · [Italiano](README.it.md)

[![License: AGPL v3+](https://img.shields.io/badge/License-AGPL_v3%2B-blue.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-0.1.0-informational)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
[![Internet](https://img.shields.io/badge/internet-no_access-brightgreen.svg)](#permissions)

**Formica** is an Android app to manage the **household pantry** and the **shopping
list**, shared with the family by exchanging a file between phones. It works without
internet, without an account and without a server.

> **In development.** There is no release yet. This page describes what version 1.0
> will do.

## What version 1.0 will do

- **Pantry**: products with quantities (decimals too), units, category, location
  (fridge, freezer, cellar and any others you choose) and notes.
- **Expiry dates**: several packs of the same product with different expiry dates;
  "opened on", with the days it keeps after opening; one daily summary notification.
- **Homemade and from producers**: preserves, yogurt, fermented food and many others,
  with the preparation date and the day they are ready; purchases in large quantities.
- **Shopping list**: items linked to the pantry or free text, minimum stock, products
  running low and products bought often, grouped by category or by shop.
- **Best price**: for each product, the shop and the best price, noted by hand.
- **Shared in the family**: export a file and open it on another phone, for example by
  sending it in a chat. Changes made on several phones are merged. Before every import
  the app shows a preview and saves a backup, so the import can be undone.
- **Languages**: English, Italian, French, German and Spanish.

## Privacy

- **No internet access**: the app has no permission to use the network.
- No account, server, ads or trackers. Open-source libraries only (AndroidX, Kotlin).
- Android's automatic backup is turned off: data leaves the phone only when you export
  a file.
- The exported file is a plain JSON file, not encrypted: whoever receives it can read
  its content.

## Permissions

| Permission | Why |
|---|---|
| Notifications (`POST_NOTIFICATIONS`) | The daily reminder about expiry dates and preparations that are ready. From Android 13 it is asked for, and it can be denied. |
| Run at startup and in the background (`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`) | Added by WorkManager, the Android library that schedules the reminder, also after a restart. Granted automatically. |
| `io.github.nidamanx.formica.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Declared by the app itself, through AndroidX: it stops other apps from sending it internal messages. No other app can obtain it, and it gives access to no data. |

No other permission. Every build checks the list and stops if a different one appears.

## Installation

From the first release the signed APK will be on the
[Releases](https://github.com/nidamanx/formica/releases) page, and later also on
F-Droid.

Release signing certificate, to compare for example with
[AppVerifier](https://github.com/soupslurpr/AppVerifier):

    io.github.nidamanx.formica
    5A:73:FC:B9:DB:E5:53:D0:F7:18:7F:56:04:90:4C:EC:19:DD:77:6B:AB:1B:0A:C2:8D:17:69:1D:88:B6:B3:9C

## Building from source

JDK 17 and the Android SDK are needed:

    ./gradlew assembleDebug

Releases are built and signed by GitHub Actions only. Details in
[docs/MAINTAINING.md](docs/MAINTAINING.md).

## License

[GNU Affero General Public License v3.0 or later](LICENSE).

Some icons come from [Material Icons](https://github.com/google/material-design-icons) by Google,
under the Apache License 2.0.
