# XOOPS Support — Feature Tutorial

Hands-on guide for the **XOOPS Support** PhpStorm plugin (`org.xoops.plugin.support`).

| | |
| --- | --- |
| Repository | [github.com/XOOPS/phpstorm-plugin](https://github.com/XOOPS/phpstorm-plugin) |
| Install ZIP | [Releases](https://github.com/XOOPS/phpstorm-plugin/releases) or `build/distributions/xoops-support-*.zip` |
| What's New | **Settings → Plugins → XOOPS Support → What's New** |

## 1. Install

```bash
./gradlew buildPlugin
# Windows: gradlew.bat buildPlugin
```

Then **Install Plugin from Disk…** → `build/distributions/xoops-support-*.zip` → restart.

## 2. Project detection

1. Open a XOOPS tree (this repo works).
2. Optional balloon: “XOOPS Support active”.
3. **Tools → XOOPS Support → Show XOOPS Project Info**.
4. **Settings** (search “XOOPS Support”): enable/disable, profile, suppress notification.

## 3. Tool window / scanner

1. **View → Tool Windows → XOOPS Support**
2. **Refresh** (or **Tools → XOOPS Support → Refresh XOOPS Overview**)
3. Click findings to open files.

## 4. Inspections + Alt+Enter

1. Copy `test-fixtures/bad_module_sample.php` under `htdocs/modules/_xoops_demo/`.
2. Open it — highlights for guards, query/exec, Request, etc.
3. **Alt+Enter** on each highlight and apply the fix.
4. For Smarty: a `.tpl` with bare `{if …}` should offer delimiter conversion.

## 5. Live templates

In a PHP file, type the abbreviation and press **Tab**:

| Abbreviation | Inserts |
| --- | --- |
| `xoguard` | `defined('XOOPS_ROOT_PATH') \|\| exit(...)` |
| `xofetch` | query + isResultSet + fetchArray (`$this->db`) |
| `xofetchdb` | same with `$db` |
| `xohead` | PHPDoc file header |
| `xolang` | `define('_MI_…')` |
| `xocriteria` | CriteriaCompo stub |
| `xorequest` | `\Xmf\Request::getString` |
| `xoexec` | `$this->db->exec(...)` |

## 6. Language constants

Type `_MI_` (or `_AM_`, `_MD_`, …) and **Ctrl+Space** — completions from `language/**/*.php` defines.

## 7. New module

**Tools → XOOPS Support → New XOOPS Module Stub…** or **New → XOOPS Module…**

- **Legacy only** — `xoops_version.php`, `index.php`, language, template
- **Hybrid** — plus `composer.json`, `src/Service/…`, `config/`, `AGENTS.md`

## 8. Actions summary

| Action | Purpose |
| --- | --- |
| Show XOOPS Project Info | Dialog: profile, web root, modules |
| Refresh XOOPS Overview | Rescan + tool window |
| New XOOPS Module Stub… | Scaffold module |

## 9. Development (sandbox)

```powershell
gradle runIde
# other terminal after edits:
gradle buildPlugin --continuous
```

Focus sandbox to hot-reload (not under debugger).
