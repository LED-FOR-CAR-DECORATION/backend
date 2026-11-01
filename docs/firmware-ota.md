# Wi-Fi firmware OTA (mainflow 4)

The owner phone sends control commands over the existing BLE link. ESP32 downloads
the application directly from the configured HTTPS backend over Wi-Fi, including
the same phone's 2.4 GHz Internet hotspot. No second phone is required.

## Setup

1. Deploy backend behind HTTPS with a public certificate chain. Apply
   `migrations/V20261029_01__firmware_ota.sql` once through the deployment migration
   runner before starting a production profile (`ddl-auto=validate`). Existing
   development `ddl-auto=create` is destructive and must never target real data.
2. Copy `IoT-Device/include/ota_config.example.h` to `ota_config.h`. Set
   `OTA_API_BASE` to the origin without trailing slash, and `OTA_ROOT_CA` to its
   root CA PEM. No credentials/private keys go here. Do not disable TLS validation.
   ESP32 needs NTP access for certificate validity checks.
3. Build firmware with `pio run -e esp32dev`. Bootstrap an existing LED once over
   USB using the intended serial port. Keep `min_spiffs.csv`; do not erase flash or
   upload SPIFFS. The old firmware has no online download command and cannot
   bootstrap this change from the phone.
4. Build Android with `./gradlew.bat :app:assembleDebug -PbackendUrl=https://YOUR_HOST`.
   HTTPS backend URL must match the server configured in firmware. With a backend
   configured, login uses real backend email/password and refreshes expired JWTs.
   Without it, existing prototype login remains, but OTA fails closed. Register
   and verify a customer account using the existing website before mobile login.
5. On mobile: connect LED, go to Account → Cập nhật firmware. Record the displayed
   device ID (12 uppercase hex characters from ESP32's base eFuse MAC, not its BLE
   address) and hardware revision. On web `/admin/firmware`, Admin registers that
   ID for the verified owner's backend account ID. Customers cannot self-claim a
   MAC. A duplicate binding is rejected; ownership transfer is a separate process.
6. For the next release, increment `FIRMWARE_VERSION` in `config.h` and build again
   with the same HTTPS/CA settings. Upload only `.pio/build/esp32dev/firmware.bin`,
   using matching version/hardware fields. Choose Phát hành. Mobile checks for the
   compatible release, configures Wi-Fi/hotspot and starts updating.

## Contract

All metadata responses use `{data: ...}`. Errors use the shared error envelope.

| Method/path | Authorization / behavior |
| --- | --- |
| GET `/api/management/admin/firmware` | ADMIN, release history |
| POST same path | ADMIN, multipart `file`, `version`, `hardwareRevision` |
| PATCH `.../{id}/activate` | ADMIN, select active release for that hardware; explicit older release supports rollback |
| PATCH `.../{id}/deprecate` | ADMIN, stop new sessions and subsequent downloads |
| POST `.../devices` | ADMIN, JSON `deviceId`, `ownerAccountId`, `hardwareRevision` |
| GET `/api/devices/{id}/ota/check?currentVersion=...` | JWT, device owner only |
| POST `/api/devices/{id}/ota/sessions` | JWT, owner only; JSON `currentVersion`, `nonce` |
| GET `/api/firmware/downloads/{token}/manifest` | 256-bit opaque capability, 15 minute TTL, no cache |
| GET `/api/firmware/downloads/{token}/binary` | Same capability; `application/octet-stream`, Content-Length, no cache |

**Binary transport exception:** the binary route intentionally returns raw `.bin`
bytes instead of the JSON envelope so ESP32 can stream them into the inactive OTA
partition without loading a base64/JSON copy into RAM. Metadata retains the normal
envelope. This exception is confined to this route and implements the requested
firmware transport. No schema migration framework has been added to the existing
development initialization exception.

BLE requests (short request IDs, max 8 characters from this app):

```json
{"command":"get_ota_info","request_id":"12345678"}
{"command":"wifi_config","ssid":"YOUR_2_4_GHZ_WIFI","password":"..."}
{"command":"ota_start","request_id":"87654321","token":"64_lowercase_hex_characters"}
```

`ota_info` includes `device_id`, `version`, `hardware`, a random 32-hex `nonce`,
`wifi`, `configured`, `busy`, and `healthy`. `ota_status` includes `request_id`,
`state` (`starting`, `downloading`, `rebooting`, `error`), `progress`, `error`.

Server manifest binds the capability to the owner, device ID, hardware, and the
nonce issued by the running LED. ESP32 only fetches from its compiled HTTPS origin;
BLE cannot supply a host, checksum or arbitrary firmware URL. The nonce rotates
when an attempt starts. Capabilities are stored only as SHA-256 in the database.
TLS and the trusted server authenticate the manifest; there is no standalone image
signing/secure boot in this implementation. Do not describe SHA-256 as a signature.

App reconnects to the same BLE MAC, checks the base device ID, expected product
version and healthy state before reporting success. A pending target version is
saved locally so reopening the screen can inspect an uncertain result. BLE loss
does not abort the device download. Errors/timeouts never claim successful OTA.

## Validation and recovery

Upload checks extension, size <= 0x1e0000, ESP32 chip/header, segment lengths,
image XOR checksum and appended digest, plus retained `CARLED_HW:<revision>` and
`CARLED_FW:<version>` application markers. ESP32 verifies full downloaded SHA-256
against the HTTPS manifest, then runs ESP image validation before switching boot
partition. Device identity, ownership, BLE data, NVS Wi-Fi and SPIFFS art are not
erased. All releases are retained (at least the latest three when three exist).

The OTA worker has connection/read timeouts and a five minute transfer ceiling.
Writes use the inactive slot, so a download interruption leaves the active app
selected. Firmware rejects concurrent projection writes/new Wi-Fi changes during
OTA. A revoked release cannot start another manifest/binary request; an already
streaming response cannot be recalled.

Stock PlatformIO Arduino uses a prebuilt bootloader. Application-level recovery
records the previous slot and attempted boot in a dedicated `ota_state` namespace:
if a new app restarts after entering `setup()` but before completing 15 seconds of
responsive main-loop startup, it selects the previous image on next startup.
Failures before `setup()` require a bootloader built with
`CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE`; this repository does not replace that
prebuilt bootloader. With such a bootloader, the app marks pending images valid
after this startup window. First bootstrap from legacy firmware must be tested
with a known good image: legacy images do not implement this recovery marker.

Existing BLE control is not cryptographically restricted to one bonded phone in
the current source. This feature preserves that control protocol and enforces OTA
through owner-authenticated backend capabilities. It does not claim to retrofit a
complete one-phone bonding/security policy for all existing BLE commands. Avoid
exposing the existing LAN control portal on an untrusted network.

## Verification

- Backend: `./mvnw.cmd -Dtest=FirmwareFlowTest -DfirmwareImage=D:/SEP/IoT-Device/.pio/build/esp32dev/firmware.bin test`.
- Android: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug -PbackendUrl=https://YOUR_HOST`.
- Web: `npx tsc --noEmit`.
- Device: `pio run -e esp32dev` (build only).

Hardware acceptance still needs two versioned builds and a real ESP32 + phone:
hotspot download, wrong owner/hardware, invalid hash, lost Wi-Fi, interrupted power,
reboot/reconnect, early crash recovery, identity/art preservation, revoked release,
and restoring the previous stable release. A software build cannot establish OTA
reliability on the physical board or guarantee the draft target of <60 seconds.

Reverse proxies/access logs must redact `/api/firmware/downloads/<token>` paths.
The application request logger does so; never export capability paths to analytics.
Expire session records with a deployment retention task after their TTL; they are
not an authoritative device-version/health log.
