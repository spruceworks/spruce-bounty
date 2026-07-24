# SpruceBounty

Free bounty plugin for Donut-like / Lifesteal SMPs. Built for **Paper 26.2**,
**Java 25**, updated within 72h of every Paper drop. Part of the SpruceWorks
free funnel — see the closest modern competitor, BetterBounty, for context on
the bar this matches and beats (26.2 support day one, anti-abuse,
PlaceholderAPI, a premium upgrade path).

**Folia support: in testing.** All scheduler access goes through
`SchedulerAdapter`, but it currently only wraps the standard Bukkit
scheduler — no Folia region/entity scheduler path exists yet. `folia-
supported` is deliberately unset in `plugin.yml` until a Folia 26.x build is
actually booted and set/claim is verified there. We never claim what we
haven't run.

## Requirements

- A Paper 26.2 server on Java 25.
- [Vault](https://www.spigotmc.org/resources/vault.34315/) **and an economy
  plugin** registered with it (e.g. EssentialsX). Hard dependency — the
  plugin logs a clear error and disables itself if neither is present.
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
  is optional; placeholders are only registered if it's installed.

## Commands

| Command | Permission | Notes |
|---|---|---|
| `/bounty set <player> <amount>` (or `/bounty <player> <amount>`) | `sprucebounty.set` | Withdraws via Vault; multiple placers stack. |
| `/bounty list` | `sprucebounty.list` | Paginated GUI, highest-first, sort toggle. |
| `/bounty check [player]` | `sprucebounty.check` | Defaults to yourself. |
| `/bounty top` | `sprucebounty.top` | Top 10 by amount. |
| `/bounty cancel <player>` | `sprucebounty.cancel` | Refunds your own contribution only (config `cancel.refund-percent`, default 75%). |
| `/bountyadmin remove <player>` | `sprucebounty.admin` | Removes the whole bounty, refunding every contributor (config `admin-actions.refund-percent`, default 100%). |
| `/bountyadmin clear` | `sprucebounty.admin` | Clears every bounty, refunding every contributor the same way. |
| `/bountyadmin reload` | `sprucebounty.admin` | Reloads config.yml + messages.yml. |

`sprucebounty.immune` (default: false) stops a bounty from being placed on
that player.

## Placeholders (PlaceholderAPI)

`%sprucebounty_own%` · `%sprucebounty_top_name%` · `%sprucebounty_top_amount%`

## Anti-abuse

- No self-bounty; no bounty on `sprucebounty.immune` players.
- Per killer→victim claim cooldown (default 30 minutes): a repeat kill during
  the cooldown pays nothing and **keeps the bounty active**.
- The cooldown is keyed by the killer→victim *pair*, not by a specific
  bounty. If a killer claims a bounty on a victim and someone immediately
  places a brand-new bounty on that same victim, the same killer still can't
  get paid for killing them again until the cooldown expires. This is
  intended anti-farm behavior, not a bug.
- Kills between players sharing an IP are ignored by default
  (`anti-abuse.ignore-same-ip-kills`).
- Non-player damage never pays out (Bukkit only sets a PvP killer on
  `PlayerDeathEvent`).

## What's inside

- `storage/` — `BountyStorage` interface + `SqliteBountyStorage` (schema
  v1, WAL mode, single synchronized connection — no pooling library for a
  single embedded file). A MySQL implementation is a config change away, not
  a rewrite (CLAUDE.md storage standard).
- `math/` — `BountyMath` (tax burn, refund %, stacking) and `CooldownGate`,
  pure functions with no Bukkit dependency, covered by unit tests.
- `service/` — `BountyService` (in-memory cache + Vault + async persistence)
  and `AntiAbuseService`.
- Vault calls run on the main thread (most Economy implementations aren't
  thread-safe); storage writes are dispatched off-thread via the scheduler
  wrapper. Never blocks the main thread on disk I/O.
- `command/`, `gui/`, `listener/`, `placeholder/` — presentation layer over
  the service; all player-facing text is MiniMessage from `messages.yml`.

## Known limitations (v1)

- A player literally named `set`, `list`, `check`, `top`, or `cancel` can't
  use the implicit `/bounty <player> <amount>` shorthand — the explicit
  `/bounty set <name> <amount>` always works.
- `/bounty check` / `/bounty cancel` / `/bountyadmin remove` resolve offline
  players only if they currently have (or recently had) a bounty tracked by
  this plugin — name resolution deliberately never makes a blocking
  Mojang API call.
- No search in the GUI (see roadmap below).

## Premium roadmap (explicitly NOT in this free v1)

Bounty boosters/escalation, decay over time, hunter streaks & leaderboard
rewards, Discord webhooks, MySQL/cross-server, importers from
BountyHunters/BetterBounty, GUI search. Item bounties are deliberately
excluded permanently, not just deferred — item escrow is dupe-prone and
off-brand.

## Usage

```console
./gradlew build        # plugin jar → build/libs/spruce-bounty-<version>.jar (shaded)
./gradlew runServer    # boot a local Paper 26.2 test server with the plugin
./gradlew test         # unit tests (money math, cooldown gate)
```

The first `runServer` stops and asks you to accept the Minecraft EULA: open
`run/eula.txt`, set `eula=true`, run again. The `run/` directory (server
files, worlds) is disposable and gitignored. Vault + an economy plugin are
not installed automatically — drop their jars into `run/plugins/` to test the
economy flow locally.

## Release checklist

1. Bump `version` in `build.gradle.kts` (semantic versioning).
2. Update `CHANGELOG.md`.
3. Register a real bStats service id at [bstats.org](https://bstats.org) and
   set `SpruceBountyPlugin.BSTATS_SERVICE_ID`.
4. `./gradlew build`, then boot the jar on the latest Paper build and check
   the console for warnings or errors.
5. Strategy-session code review before any marketplace listing.
