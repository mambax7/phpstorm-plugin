# Changelog

All notable changes to **XOOPS Support** (`org.xoops.plugin.support`) are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project uses [Semantic Versioning](https://semver.org/) with pre-release tags
(`1.0.0-alpha.N`).

## Unreleased

_Nothing yet._

## [1.0.0-alpha.3] — 1.0.0 Alpha 3 — 2026-09-21

### Fixed (Goffy / wgSimpleAcc field report)

- Inspections no longer report every finding twice (file-level `visitFile` now runs only on the view-provider's primary PSI file).
- `isResultSet` quick-fix inserts before the current PSI statement (Inspect Code batch apply no longer no-ops after the first click in a file).
- An early-exit `isResultSet` / `mysqli_result` guard covers later `fetch*` of the same variable, including `while (list(...) = $db->fetchRow($result))`.
- ROOT_PATH guard: `die` and `exit` after `namespace` / `use` are recognized; the quick-fix inserts *after* the namespace (never before — invalid PHP).
- ROOT_PATH guard is not required on 404/403 directory stubs, `admin/` CP scripts, `xoops_version.php`, or files whose first work is including `mainfile.php` / `header.php` / `admin_header.php`.

### Fixed (release review, PR #4)

- Replaced deprecated `StartupActivity` with `ProjectActivity`.
- `FilenameIndex.getVirtualFilesByName` now passes `Project` (the two-arg overload is deprecated).
- Marketplace Plugin Verifier **Critical** on IntelliJ IDEA is the missing `com.jetbrains.php` plugin in IU, not a PhpStorm incompatibility — verify against PhpStorm.
- Persist Alpha 2 `coreProfile` into `coreVersion` (legacy-only XML keeps 2.5/2.7/4.0; explicit new `Auto` is not overwritten).
- Batch isResultSet quick-fix reuses inspection analysis at the fetch offset; reassignment inside a positive `if` is unguarded.
- ROOT_PATH open-tag handling is shared (`<?php` and `<?`); short-tag insert-after-`<?` fallback removed.
- Language-constant index keeps original spelling; resolution is exact-case (name recognition stays case-insensitive). Only a `$smarty.const.` prefix is stripped.
- isResultSet reassignment ends at `;`, `,`, an unmatched closer, or `or`/`and`/`xor`; `?:`, `??`, `||`, `&&` keep the fetch inside the assignment.
- ROOT_PATH guard is still required for includes that start with HTML (quick-fix declines without a file-leading open tag); stub detection ignores `;` inside quotes.
- `xor` in an early-exit condition is not a dominating isResultSet guard; language-constant cap is enforced before the 5001st name; scanner accepts `templates/…` and `blocks/…` manifest spellings and honours Cancel while walking template trees; the unregistered-template inspection applies the same manifest-spelling rule. A block template at `templates/blocks/foo.tpl` registered as `foo.tpl` (the XOOPS convention) is neither "unregistered" nor "missing"; `$modversion['blocks'][1]['template'] = '…'` assignment syntax is read as a registration alongside `'template' => '…'`; commented-out entries are ignored by the scanner too. A positive `isResultSet(...) xor …` guard is not a guard. The comment mask steps over quoted strings, so `//` or `#` inside a string (a URL in a description) no longer hides the rest of the line from the manifest, guard and template readers. A `module.json`-only module is not scanned for unregistered templates. The missing-registered-template inspection reads the manifest through the comment-only mask (it had masked the string literals it needed and never reported). `define()` must be a real call (`mydefine('_MI_…')` is not indexed). The register-template quick-fix ignores commented-out entries. A positive `isResultSet` guard does not cover a fetch inside a closure. The ROOT_PATH quick-fix is attached only where it can insert. An unreadable or oversized `xoops_version.php` yields one `SCAN_ERROR` instead of every template being reported unregistered. Commented-out `define()` lines are not indexed as language constants. Heredoc/nowdoc bodies are skipped by the comment-only mask, so `//` or `/*` inside them cannot hide later code. A VFS event on the `language` directory itself invalidates the constant cache. A `define()` inside a string literal is not indexed. The isResultSet quick-fix inserts before the enclosing unbraced `if`/`while`/`for` rather than inside it. Bootstrap detection matches exact filenames (`custom-header.php`, `mainfile.php.bak` do not count). Template registrations are read only from `$modversion['templates']` / `$modversion['blocks']` statements (shared `XoopsManifestTemplates` reader). An `elseif` early exit is not a dominating guard. Core version detection ignores `12.5` / `12.7` / `14.0`. The isResultSet quick-fix declines when the statement assigns the result before fetching. Ctrl+B prefers a `define()` in the same module when several modules define the same name.

### Added

- Language-constant completion scans every `language/**/*.php` (not a five-name allowlist).
- Ctrl+B / Find Usages on `_MI_` / `_AM_` / `_MD_` / `_CO_` / `_MB_` constants (resolves to `define()` in `language/english/` when present).
- Inspection: `.tpl` on disk under `templates/` or `blocks/` not listed in `xoops_version.php` (inverse of missing registered template), with a register-in-manifest quick-fix.
- Inspection: `XOBJ_DTYPE_UNICODE_*` deprecated since 2.7.3, rename quick-fix to the non-UNICODE successor (silent when Core Version is 2.5).
- JUnit 4 analyzer / policy / scanner / plugin.xml tests.

### Changed

- Plugin version is taken only from `gradle.properties` (`plugin.xml` no longer hard-codes `<version>`).

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

First public alpha of **XOOPS Support** — a PhpStorm / IntelliJ helper for XOOPS 2.5 / 2.7 / 4.0 Core and module development.

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
- **Settings** — enable/disable, suppress startup notification, Core Version, table prefix
- **Dynamic plugin** — no `require-restart`; install / disable / enable without IDE restart when unload succeeds
- **CI / release** — GitHub Actions (`check`, `verifyPlugin`, `buildPlugin`); tag `v*` must match `pluginVersion`
- **Compatibility** — PhpStorm **2024.3+** (`since-build=243`, open-ended `until-build` for 2025.x / 2026.2.x)

### Notes

- Inspections use conservative text heuristics (comment/string masking); not a full PHP CFG
- Result-set guards reject ambiguous OR/AND conditions (including parenthesized forms)
- Overview scans are sequenced so a slower older scan cannot overwrite a newer refresh
- License: GPL-2.0 (SPDX **GPL-2.0-or-later** in packaging docs)

[1.0.0-alpha.3]: https://github.com/XOOPS/phpstorm-plugin/releases/tag/v1.0.0-alpha.3
[1.0.0-alpha.2]: https://github.com/XOOPS/phpstorm-plugin/releases/tag/v1.0.0-alpha.2
[1.0.0-alpha.1]: https://github.com/XOOPS/phpstorm-plugin/releases/tag/v1.0.0-alpha.1
