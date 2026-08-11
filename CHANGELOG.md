# Changelog

All notable changes to **XOOPS Support** (`org.xoops.plugin.support`) are documented here.

## [1.0.0-alpha.1] — 1.0.0 Alpha 1 — 2026-08-11

First public alpha.

### Added
- **XOOPS Support** for PhpStorm / IntelliJ: inspections with Alt+Enter quick fixes, live templates,
  language-constant completion, project scanner / tool window, hybrid module scaffold, settings
- Live templates: `xoguard`, `xofetch`, `xofetchdb`, `xohead`, `xolang`, `xocriteria`, `xorequest`, `xoexec`
- Language-constant completion for PHP and Smarty
- **New → XOOPS Module…** and **Tools → XOOPS Support** actions
- Dual Alt+Enter fixes for `queryF()` (`query` / `exec`) when SQL is unclear
- Safe project detection (`ReadAction` + smart mode)
- Sandbox auto-reload for plugin development
- GitHub CI (`check verifyPlugin buildPlugin`) and release workflow

### Notes
- Alpha quality: expect rough edges; please report issues with PhpStorm version and a minimal repro.
