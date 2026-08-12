# XOOPS Support

**XOOPS Support** is a [PhpStorm](https://www.jetbrains.com/phpstorm/) / IntelliJ plugin for [XOOPS](https://xoops.org) module and core development (2.5 / 2.7 / 4.0).

It brings XOOPS conventions into the IDE: inspections with Alt+Enter quick fixes, language-constant completion, live templates, a project scanner, and module scaffolding.

| | |
| --- | --- |
| Repository | [github.com/XOOPS/phpstorm-plugin](https://github.com/XOOPS/phpstorm-plugin) |
| Plugin id | `org.xoops.plugin.support` |
| Version | **1.0.0 Alpha 1** (`1.0.0-alpha.1`) |
| Compatibility | PhpStorm **2024.3+** (since-build `243`, no upper cap — includes **2026.2.x**) |
| License | [GPL-2.0-or-later](LICENSE) |

## Features

- **Inspections + quick fixes** — root-path guards, `isResultSet` before `fetch*`, `query()` vs `exec()`, deprecated `queryF`/`quoteString`, missing registered templates, wrong Smarty delimiters, `include` → `include_once` for headers. For input: **keyed** `$_GET`/`$_POST`/`$_COOKIE['key']` offer `\Xmf\Request::getString` fixes; **bare** `$_GET`/`$_POST`/`$_REQUEST`/`$_COOKIE` and **keyed `$_REQUEST`** are warnings only (no auto-fix when the source is ambiguous)
- **Live templates** — `xoguard`, `xofetch`, `xofetchdb`, `xohead`, `xolang`, `xocriteria`, `xorequest`, `xoexec`
- **Language constants** — completion for `_MI_` / `_AM_` / `_MD_` / … from `language/**/*.php`
- **Project tools** — detection balloon, scanner tool window, **Tools → XOOPS Support**
- **Module scaffold** — legacy or hybrid (PSR-4 / composer) via **New → XOOPS Module…**

See [TUTORIAL.md](TUTORIAL.md) for a walkthrough and [CHANGELOG.md](CHANGELOG.md) for release notes.

## Install (from a release ZIP)

1. Download `xoops-support-*.zip` from [Releases](https://github.com/XOOPS/phpstorm-plugin/releases) (or build locally).
2. PhpStorm → **Settings → Plugins → ⚙ → Install Plugin from Disk…**
3. If PhpStorm offers restart, accept once after the first install (later updates often apply without restart).
4. Open a XOOPS tree → **Tools → XOOPS Support**.

## Build from source

**Requirements:** JDK **21+**, network access (downloads PhpStorm SDK on first build).

```bash
./gradlew buildPlugin
# Windows: gradlew.bat buildPlugin
```

Plugin ZIP:

```text
build/distributions/xoops-support-<version>.zip
```

### Useful tasks

| Task | Purpose |
| --- | --- |
| `./gradlew buildPlugin` | Package the plugin ZIP |
| `./gradlew runIde` | Sandbox PhpStorm with the plugin (auto-reload on) |
| `./gradlew verifyPlugin` | IntelliJ Plugin Verifier |
| `./gradlew check verifyPlugin buildPlugin` | Full CI-style build |

After `runIde`, rebuild with `./gradlew buildPlugin` (or `--continuous`) and focus the sandbox to hot-reload (not under a debugger).

## Continuous integration

GitHub Actions workflow (same idea as [php-hammer](https://github.com/hammer-tools/php-hammer/blob/master/.github/workflows/gradle.yml)):

- **`.github/workflows/gradle.yml`** — on push/PR to `main`/`master`: `./gradlew check verifyPlugin buildPlugin`, upload ZIP artifact
- **`.github/workflows/release.yml`** — on tag `v*`: build and attach ZIP to a GitHub Release

## Project layout

```text
xoops-support/
  .github/workflows/     # CI + release
  src/main/java/org/xoops/support/
  src/main/resources/META-INF/plugin.xml
  src/main/resources/liveTemplates/
  src/main/resources/inspectionDescriptions/
  test-fixtures/         # sample “bad” PHP/Smarty for manual testing
  build.gradle.kts
  gradle.properties
```

## Configuration

Edit `gradle.properties` for version and platform target:

```properties
pluginVersion=1.0.0-alpha.1
platformVersion=2024.3.5
pluginSinceBuild=243
pluginUntilBuild=          # empty = open-ended (2026.2+)
```

## Contributing

1. Fork and clone this repository.
2. Open the project in IntelliJ IDEA / PhpStorm (import as Gradle project).
3. Use JDK 21.
4. Run `./gradlew runIde` for a sandbox.
5. Open a PR against `main` (or `master`) on [XOOPS/phpstorm-plugin](https://github.com/XOOPS/phpstorm-plugin).

## Links

- [GitHub repository](https://github.com/XOOPS/phpstorm-plugin)
- [Issues](https://github.com/XOOPS/phpstorm-plugin/issues)
- [XOOPS](https://xoops.org)
- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- Inspired by framework-focused IDE plugins such as [PHP Hammer](https://github.com/hammer-tools/php-hammer)

## License

**GPL-2.0-or-later** — see [LICENSE](LICENSE) (verbatim GNU GPL v2 text).

Copyright (C) 2026 XOOPS Project and contributors.
