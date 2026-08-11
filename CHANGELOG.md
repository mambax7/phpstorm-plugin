# Changelog

All notable changes to **XOOPS Support** (`org.xoops.plugin.support`) are documented here.

## [1.0.0-alpha.3] — 1.0.0 Alpha 3 — 2026-08-11

Addresses [PR #1](https://github.com/XOOPS/phpstorm-plugin/pull/1) review feedback (CodeRabbit + Sourcery).

### Fixed
- **Module scaffold `index.php`** no longer guards before `mainfile.php` (entry point always died)
- **`$_REQUEST` quick fix** offers GET / POST / COOKIE choices instead of silently using GET
- **Root-path guard QF** null-safe; finds any `<?php` open tag (does not assume offset 0)
- **Quick fixes** revalidate expected text before editing (stale-offset safety)
- **Result-set guard** matches `isResultSet($var)` for the real result variable; insert body is scope-neutral
- **Root-path inspection** highlights open-tag leaf, not the whole file
- **`xofetchdb` / `xocriteria`** live templates produce valid PHP defaults
- **Show Project Info** and startup detection run off the EDT
- **Tool window** restores UI when a scan throws
- **Profile detection** prefers `include/version.php` over raw `composer.json` ranges
- **Locale.ROOT** for path/prefix case conversion
- **Settings `isModified`** null-safe profile comparison
- **CI** `persist-credentials: false`; release tag must match `pluginVersion`
- **LICENSE** restored to verbatim GPL-2.0; SPDX **GPL-2.0-or-later** in docs
- Tool-window icon sized for the strip (`icons/toolWindowXoops.svg`)

### Added
- **Language-constant cache** (`XoopsLanguageConstantsCache`) with VFS invalidation

## [1.0.0-alpha.2] — 1.0.0 Alpha 2 — 2026-08-11

### Fixed
- **PhpStorm 2026.2.x install** — open-ended `until-build` (`since-build=243` only)

## [1.0.0-alpha.1] — 1.0.0 Alpha 1 — 2026-08-11

First public alpha (inspections, templates, scanner, scaffold, CI).
