# Changelog

All notable changes to this project are documented in this file. The format is
based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-07-28

### Changed

- **Packaging: the SQLite driver is no longer bundled.** `org.xerial:sqlite-jdbc`
  is declared in `plugin.yml`'s `libraries:` block and fetched by Paper's
  library loader at startup instead of being shaded in. The bundled driver
  carried native binaries for every supported platform — about 24 MB of the
  jar — which pushed the download over SpigotMC's upload limit.
  **Jar size: 13.76 MB → 97 KB.**
- The driver is now registered explicitly rather than through JDBC 4
  auto-discovery, so a failed download reports one clear message instead of
  "No suitable driver found".

### Upgrade note

**The first server start after installing needs outbound access to Maven
Central**, so the driver can be downloaded. It is then cached in the server's
`libraries/` folder and every later start works offline. Nothing else changes:
existing `bounty.db` files are read unchanged — verified by loading a database
written by 1.0.0 and confirming all rows survive a restart.

bStats stays bundled (53 KB), so metrics never depend on a network fetch at
startup.

## [1.0.0] - 2026-07-25

### Added

- Initial v1.0 implementation per SPEC-free-plugin-1.md: `/bounty`
  (set/list/check/top/cancel) and `/bountyadmin` (remove/clear/reload)
  commands, Vault economy integration (withdraw on placement, placement tax,
  stacking, per-contributor refund on cancel), PvP claim with per
  killer→victim cooldown and same-IP guard, paginated bounty-list GUI,
  PlaceholderAPI expansion, SQLite storage behind a versioned interface, unit
  tests for the money math and cooldown gate.
- Not yet published to any marketplace.

### Changed

- Strategy-session code review fixes: removed the unverified "Folia-aware"
  claim from `plugin.yml`'s description (Folia support stays "in testing" in
  the README until an actual Folia 26.x boot is verified); `/bountyadmin
  remove` and `/bountyadmin clear` now refund every contributor at
  `admin-actions.refund-percent` (default 100%) instead of burning their
  money as a side effect of moderation.
- SPEC-ops-1 Part B: rechecked Folia 26.x availability (2026-07-24) via
  PaperMC's Fill API — still only 26.1 has real builds, `folia-supported`
  stays unset. README now cites the exact evidence instead of a bare claim.
- Registered the real bStats service id (32880) in place of the placeholder,
  so metrics report under this plugin's own listing.
