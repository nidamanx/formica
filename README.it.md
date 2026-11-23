# Formica

[English](README.md) · **Italiano**

[![Licenza: AGPL v3+](https://img.shields.io/badge/License-AGPL_v3%2B-blue.svg)](LICENSE)
![Versione](https://img.shields.io/badge/version-0.1.0-informational)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84)
[![Internet](https://img.shields.io/badge/internet-no_access-brightgreen.svg)](#autorizzazioni)

**Formica** è un'app Android per gestire la **dispensa di casa** e la **lista della
spesa**, da condividere in famiglia scambiando un file tra i telefoni. Funziona senza
internet, senza account e senza server.

> **In sviluppo.** Non esiste ancora una release. Questa pagina descrive ciò che farà la
> versione 1.0.

## Cosa farà la versione 1.0

- **Dispensa**: prodotti con quantità anche decimali, unità, categoria, posizione (frigo,
  freezer, cantina e altre a scelta) e note.
- **Scadenze**: più confezioni dello stesso prodotto con scadenze diverse; "aperto il",
  con i giorni di validità dopo l'apertura; una notifica giornaliera riassuntiva.
- **Fatto in casa e dai produttori**: conserve, yogurt, fermentati e molto altro, con la
  data di preparazione e il giorno da cui sono pronti; acquisti in grandi quantità.
- **Lista della spesa**: voci collegate alla dispensa o libere, scorta minima, prodotti
  che stanno per finire e prodotti comprati spesso, raggruppati per categoria o per
  negozio.
- **Dove conviene**: per ogni prodotto, il negozio e il prezzo migliore, annotati a mano.
- **Condivisione in famiglia**: si esporta un file e lo si apre su un altro telefono, per
  esempio inviandolo in una chat. Le modifiche fatte su più telefoni si uniscono. Prima di
  ogni importazione l'app mostra un'anteprima e salva un backup, così si può annullare.
- **Lingue**: italiano, inglese, francese, tedesco e spagnolo.

## Privacy

- **Nessun accesso a internet**: l'app non ha l'autorizzazione per usare la rete.
- Nessun account, server, pubblicità o tracker. Solo librerie open source (AndroidX,
  Kotlin).
- Il backup automatico di Android è disattivato: i dati escono dal telefono solo quando
  esporti un file.
- Il file esportato è un normale file JSON, non cifrato: chi lo riceve può leggerne il
  contenuto.

## Autorizzazioni

| Autorizzazione | Perché |
|---|---|
| Notifiche (`POST_NOTIFICATIONS`) | Il promemoria giornaliero su scadenze e preparazioni pronte. Da Android 13 viene chiesta, e si può negare. |
| Avvio all'accensione e attività in background (`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`) | Aggiunte da WorkManager, la libreria di Android che programma il promemoria anche dopo un riavvio. Concesse automaticamente. |
| `io.github.nidamanx.formica.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Dichiarata dall'app stessa, tramite AndroidX: impedisce alle altre app di inviarle messaggi interni. Nessun'altra app può ottenerla e non dà accesso ad alcun dato. |

Nessun'altra autorizzazione. Ogni build controlla l'elenco e si ferma se ne compare una
diversa.

## Installazione

Alla prima release l'APK firmato sarà nella pagina
[Releases](https://github.com/nidamanx/formica/releases); in seguito anche su F-Droid.

Certificato di firma delle release, da confrontare per esempio con
[AppVerifier](https://github.com/soupslurpr/AppVerifier):

    io.github.nidamanx.formica
    5A:73:FC:B9:DB:E5:53:D0:F7:18:7F:56:04:90:4C:EC:19:DD:77:6B:AB:1B:0A:C2:8D:17:69:1D:88:B6:B3:9C

## Compilazione dai sorgenti

Servono JDK 17 e l'Android SDK:

    ./gradlew assembleDebug

Le release sono compilate e firmate solo da GitHub Actions. Dettagli in
[docs/MAINTAINING.md](docs/MAINTAINING.md) (in inglese).

## Licenza

[GNU Affero General Public License v3.0 o successiva](LICENSE).
