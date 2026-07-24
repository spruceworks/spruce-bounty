# Changelog

All notable changes to this project are documented in this file. The format is
based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
