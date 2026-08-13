# Changelog

All notable changes to **XOOPS Support** (`org.xoops.plugin.support`) are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [Semantic Versioning](https://semver.org/) with pre-release tags
(`1.0.0-alpha.N`).

## [1.0.0-alpha.2] — 1.0.0 Alpha 2 — 2026-08-12

### Fixed

- **Overview auto-scan on tool-window open** — no longer walks every module `.php`/`.tpl` when the XOOPS Support tool window is created. That path froze multi-project monorepo boot (high disk I/O / power). Default is idle until **Refresh**.
- Setting **Auto-scan project when Overview tool window opens** (off by default) for users who want the old behaviour.
- Background scan tasks are **cancellable**; the filesystem scanner calls `ProgressManager.checkCanceled()` once per module and throttled every 32 paths in file walks (avoids per-path overhead on large monorepos while staying responsive).
- Cancellation is observed during module **metadata** walks (`inspectModule` / `countFiles`), not only during source-file scanning (review feedback on PR #2).
- **Tools → Show XOOPS Project Info** rethrows `ProcessCanceledException` so Cancel does not show as a scan-failure dialog.

### Changed

- **Inspection tree placement** — all XOOPS inspections use top-level group **XOOPS** in Settings → Editor → Inspections (removed `groupPath="PHP"` so they are not buried under PHP → XOOPS).
- Overview status HTML (disabled / idle / cancelled / failed / scanning) centralized in `XoopsReportHtmlRenderer` via shared `wrapBody()` helpers.

## [1.0.0-alpha.1] — 1.0.0 Alpha 1 — 2026-08-11

First public alpha of **XOOPS Support** — a PhpStorm / IntelliJ helper for XOOPS 2.5 / 2.7 / 4.0 module and core work.

Early preview: APIs, inspections, and quick fixes may change before a stable 1.0.

### Added

- **Project detection** — startup balloon when XOOPS markers (`mainfile.php`, `xoops_version.php`) are present
- **Overview tool window** — background filesystem scan with HTML findings and click-to-open links
- **Tools → XOOPS Support** — Show Project Info, Refresh Overview, New Module Stub
- **Module scaffold** — legacy or hybrid (PSR-4 / Composer) via **New → XOOPS Module…**
- **Inspections (PHP / Smarty)** with Alt+Enter quick fixes where safe:
  - Missing `XOOPS_ROOT_PATH` direct-access guard
  - Raw superglobals (prefer `\Xmf\Request`); multi-choice QF for keyed `$_REQUEST`
  - Mutating SQL passed to `query()` (use `exec()`)
  - `fetch*` without a proven `isResultSet($result)` guard
  - Deprecated `queryF` / `quoteString`
  - Registered template missing on disk
  - `include` of header/footer instead of `include_once`
  - Wrong Smarty delimiters (XOOPS `<{ … }>` vs bare `{ … }`)
- **Live templates** — `xoguard`, `xofetch`, `xofetchdb`, `xohead`, `xolang`, `xocriteria`, `xorequest`, `xoexec`
- **Language-constant completion** — `_MI_` / `_AM_` / `_MD_` / … from `language/**/*.php`, with project cache and VFS invalidation
- **Settings** — enable/disable, suppress startup notification, core profile, table prefix
- **Dynamic plugin** — no `require-restart`; install / disable / enable without IDE restart when unload succeeds
- **CI / release** — GitHub Actions (`check`, `verifyPlugin`, `buildPlugin`); tag `v*` must match `pluginVersion`
- **Compatibility** — PhpStorm **2024.3+** (`since-build=243`, open-ended `until-build` for 2025.x / 2026.2.x)

### Notes

- Inspections use conservative text heuristics (comment/string masking); not a full PHP CFG
- Result-set guards reject ambiguous OR/AND conditions (including parenthesized forms)
- Overview scans are sequenced so a slower older scan cannot overwrite a newer refresh
- License: GPL-2.0 (SPDX **GPL-2.0-or-later** in packaging docs)

[1.0.0-alpha.1]: https://github.com/XOOPS/phpstorm-plugin/releases/tag/v1.0.0-alpha.1
