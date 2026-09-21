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

1. Open a **real XOOPS install** (with `mainfile.php` or `htdocs/mainfile.php`). This plugin repository alone is not a XOOPS site root.
2. Optional balloon: “XOOPS Support active”.
3. **Tools → XOOPS Support → Show XOOPS Project Info** (runs in the background).
4. **Settings** (search “XOOPS Support”): enable/disable, Core Version, suppress notification.

## 3. Tool window / scanner

1. **View → Tool Windows → XOOPS Support**
2. **Refresh** (or **Tools → XOOPS Support → Refresh XOOPS Overview**)
3. Click findings to open files.

![XOOPS Support Overview tool window: modules table and findings](https://plugins.jetbrains.com/files/33478/screenshot_200ef8b4-f28f-4bbd-95f3-3f310b27fed7)

**Reading the Overview.** The header line gives the totals (here 22 modules, 316 findings) and the detected core line (**XOOPS 2.7.x**, from the Core Version setting or auto-detection) with the web root the scan used.

The **Modules** table lists every directory under `modules/` that has a manifest, one row per module:

| Column | Meaning |
| --- | --- |
| Manifest | `xoops_version.php` (legacy), `module.json` (4.0), or `hybrid` when both exist |
| TPL | `.tpl` files under `templates/` |
| Lang | `.php` files under `language/` (all locales) |
| Pre | `.php` files under `preloads/` |
| Cls | `.php` files under `class/` and `src/` together |

The counts are a quick health read: **TPL 0** means the module renders nothing of its own (fine for a library module such as `protector` or `xwhoops`), **Lang** grows with every shipped locale (each adds its own set of files), and an unexpectedly large **Cls** usually means a vendored library sits under `class/` or `src/`.

**Findings** are listed per module in the same order as the table. Each line is a kind, a message and a `file:line` link that opens the editor at that spot. The kinds map onto the section 4 inspections:

| Finding | Inspection |
| --- | --- |
| `MUTATING_QUERY` | 4.6 `XoopsQueryExec` |
| `DEPRECATED_QUERY_F`, `DEPRECATED_QUOTE_STRING` | 4.1 `XoopsDeprecatedDbApi` |
| `RAW_REQUEST` | 4.7 `XoopsSuperglobal`, `$_REQUEST` only; keyed `$_GET` / `$_POST` are left to the editor |
| `MISSING_REGISTERED_TEMPLATE` | 4.8 `XoopsMissingRegisteredTemplate` |
| `UNREGISTERED_TEMPLATE` | 4.9 `XoopsUnregisteredTemplate` |
| `WRONG_SMARTY_DELIMITER` | 4.10 `XoopsWrongSmartyDelimiter` |
| `SCAN_ERROR` | a file or directory the scanner could not read |

The scanner reports the **first** occurrence per file for the code-pattern kinds, so one `RAW_REQUEST` line can stand for several uses in that file; the editor inspection marks each one. The guard and `isResultSet` rules run only in the editor, where they have the PSI they need.

Use the Overview as the whole-site view: triage a large install or a module you have just imported, then open a file and let Alt+Enter do the individual fixes. The scan runs as a background task and honours **Cancel**.

## 4. Inspections + Alt+Enter

1. Copy `test-fixtures/bad_module_sample.php` under `htdocs/modules/_xoops_demo/`.
2. Open it — highlights for guards, query/exec, Request, etc.
3. **Alt+Enter** on each highlight and apply the fix.
4. For Smarty: a `.tpl` with bare `{if …}` should offer delimiter conversion.
5. Goffy / wgSimpleAcc shapes: `test-fixtures/wgsimpleacc_shapes.php` (namespaced `die` guard + `while (list = fetchRow)`) and `test-fixtures/index_404_stub.php` (must **not** warn).

All inspections live under **Settings → Editor → Inspections → XOOPS**. Each can be switched off or downgraded there, and the Inspection ID below is what you put in a `@noinspection` comment or an `.idea/inspectionProfiles` file. Every inspection is silent in `vendor/`, `templates_c/`, `cache/` and `node_modules/`.

### 4.1 Deprecated database API — `XoopsDeprecatedDbApi`

Flags `queryF()` and `quoteString()`. Use `query()` / `exec()` and `quote()` instead.

**Why.** `queryF()` was the "force" variant that skipped the write block on `query()`. Since XOOPS 2.5.12 the split is explicit: `query()` refuses mutating SQL and `exec()` is the only sanctioned write path, so `queryF()` no longer has a job. `quoteString()` is a plain alias of `quote()` kept for backward compatibility and scheduled for removal.

```php
// before
$db->queryF('UPDATE ' . $db->prefix('news') . ' SET hits = hits + 1');
$name = $db->quoteString($name);

// after
$db->exec('UPDATE ' . $db->prefix('news') . ' SET hits = hits + 1');
$name = $db->quote($name);
```

**Quick-fixes.** `quoteString()` is renamed to `quote()`. For `queryF()` the inspection reads the first string argument to decide what to offer:

- SQL starting with `SELECT` / `SHOW` / `DESCRIBE` / `EXPLAIN`: rename to `query()` only.
- SQL starting with `INSERT` / `UPDATE` / `DELETE` / `REPLACE` / `TRUNCATE` / `ALTER` / `DROP` / `CREATE`: `exec()` is offered first, `query()` second.
- SQL that cannot be read (a variable, a concatenation): both renames are offered; pick by intent.

`exec()` takes one argument, so the `exec()` rename is withheld while the call still passes `$limit` / `$start`. Drop those first, then re-run the fix.

### 4.2 Deprecated `XOBJ_DTYPE_UNICODE_*` — `XoopsDeprecatedUnicodeDtype`

`XOBJ_DTYPE_UNICODE_*` constants were deprecated in XOOPS 2.7.3 (core issue #164) and are scheduled for removal in 4.0. The quick-fix renames the constant to its non-UNICODE successor (`XOBJ_DTYPE_TXTBOX`, `TXTAREA`, `URL`, `EMAIL`, `ARRAY`, `OTHER`). Value migration stays a core concern and is not performed here. Silent when the project Core Version is set to 2.5.

**Why.** The `UNICODE_*` types date from the pre-UTF-8 era when a field had to declare that it might hold multibyte text. Every supported core runs on `utf8mb4`, so the distinction is dead weight: the sanitizer treats `XOBJ_DTYPE_UNICODE_TXTBOX` and `XOBJ_DTYPE_TXTBOX` identically. Renaming is safe at the `initVar()` site because it changes only which constant name is looked up, not how the stored value is read or written.

```php
// before
$this->initVar('title', XOBJ_DTYPE_UNICODE_TXTBOX, null, true, 255);

// after
$this->initVar('title', XOBJ_DTYPE_TXTBOX, null, true, 255);
```

**Core Version gate.** The 2.5 LTS line never deprecated these constants, so a module that must keep running on 2.5 gets no warning. Set Core Version to **2.5** in Settings for that project; **Auto**, **2.7** and **4.0** all report.

### 4.3 `isResultSet` guard — `XoopsResultSetGuard`

Warns when `fetchArray` / `fetchRow` / `fetchBoth` appears without a dominating `isResultSet` check. Prefer the two-part guard:

```php
$result = $db->query($sql);
if (!$db->isResultSet($result) || !$result instanceof \mysqli_result) {
    throw new \RuntimeException('Database query failed');
}
while (list($id, $title) = $db->fetchRow($result)) {
    // ...
}
```

An early-exit guard covers later fetches of the same variable, including `while (list(...) = $db->fetchRow($result))`, until that variable is reassigned. The quick-fix inserts before the enclosing statement using the current PSI (safe to apply several times after Inspect Code).

**Why two parts.** `query()` returns `false` on failure, and `fetchArray(false)` is a fatal `TypeError` on PHP 8. `isResultSet()` is the XOOPS check; the `instanceof \mysqli_result` half is for static analysers such as Scrutinizer and PHPStan, which do not know that `isResultSet()` narrows the type.

**What counts as guarded.** The analysis is textual, so it follows a few explicit rules rather than full control flow:

- A negative check that throws, returns or exits (`if (!$db->isResultSet($result)) { return; }`) covers every later fetch of `$result` in the same block. It stops covering at a reassignment of `$result`, at a nested `function`, or when the enclosing `}` closes.
- A positive check (`if ($db->isResultSet($result)) { ... }`) covers fetches inside its body, brace-less body included.
- `$result = $db->fetchArray($result)` inside a guarded region stays guarded: the fetch reads the old value. `$result = false or $db->fetchArray($result)` does not, because `or` binds looser than `=` and the assignment completes first.
- `&&`, `and` and `xor` in the guard condition disqualify it. `if (!isResultSet($r) && $x) return;` can fall through while `$r` is still `false`.
- Comments and strings are masked first, so a commented-out guard never counts.

### 4.4 `include_once` for headers — `XoopsIncludeOnceHeader`

XOOPS module entry points should load `header.php` / `footer.php` with `include_once` (not bare `include`).

**Why.** `header.php` starts the theme, opens the output buffer and pulls in `$xoopsTpl`; `footer.php` renders and flushes. Including either twice, which happens easily once a page delegates to a shared `include/` file, produces duplicate headers or a blank page. `include_once` makes the second include a no-op. The rule applies to the core files and to a module's own `header.php` / `footer.php`, with or without the `XOOPS_ROOT_PATH .` prefix.

```php
// before
include XOOPS_ROOT_PATH . '/header.php';
include __DIR__ . '/footer.php';

// after
include_once XOOPS_ROOT_PATH . '/header.php';
include_once __DIR__ . '/footer.php';
```

**Quick-fix** rewrites the keyword in place. `require` / `require_once` are not touched; only a bare `include` of a `header.php` / `footer.php` path is reported.

### 4.5 Direct-access guard — `XoopsRootPathGuard`

Reports include-only PHP files (classes, preloads, includes, blocks) that lack a terminating `defined('XOOPS_ROOT_PATH') || exit/die(...)` guard. The guard must be the first executable statement after `<?php`, `declare` and `namespace`; a `use` block before or after it is fine. `die` and `exit` are both accepted. In namespaced files the quick-fix inserts the guard **after** the namespace and before the `use` block (never before `namespace`, that is invalid PHP).

Not reported: language files, vendor/cache, tests, `xoops_version.php`, `admin/` control-panel scripts, files whose first work is including `mainfile.php` / `header.php` / `admin_header.php`, and directory-protection stubs that only send HTTP 404/403.

**Why.** Files under `class/`, `include/`, `preloads/`, `blocks/`, `kernel/` and `src/` are meant to be pulled in by a bootstrapped page. Requested directly over HTTP they run without a session, without `$xoopsUser`, and with every global undefined, which is how include files turn into information leaks or code paths that skip permission checks. The guard turns a direct request into a one-line exit.

```php
<?php

namespace XoopsModules\Demo;

defined('XOOPS_ROOT_PATH') || exit('Restricted access');

use XoopsModules\Demo\Constants;

class Accounts extends \XoopsObject
{
}
```

**Placement.** PHP requires `namespace` to be the first statement, but `use` imports may follow executable code, so the guard sits between them. That is also where the wgSimpleAcc family and most Goffy modules put it. `if (!defined('XOOPS_ROOT_PATH')) { exit('...'); }` is accepted as an equivalent form.

**Why entry points are skipped.** A page that starts with `include mainfile.php` or `header.php` is bootstrapping itself and must be reachable over HTTP. `admin/` scripts include `admin_header.php` for the same reason. A guard on any of these would exit every public request. A 404/403 stub (`header('HTTP/1.0 404 Not Found');` and nothing else) is meant to answer direct requests, so a guard there would replace the intended HTTP status with "Restricted access".

**Quick-fix limits.** The fix computes the insert offset from the file, so it works after `declare(strict_types=1)`, multiple `declare` statements, `namespace Foo;` and `namespace Foo { … }`, with both `<?php` and short `<?` tags. A file that starts with HTML before its first `<?php` is still reported, but the fix declines rather than guess where the guard belongs.

### 4.6 `query()` vs `exec()` — `XoopsQueryExec`

Flags string SQL starting with `INSERT` / `UPDATE` / `DELETE` / `REPLACE` / `TRUNCATE` / `ALTER` / `DROP` / `CREATE` passed to `->query()`. Use `exec()` for mutations (XOOPS 2.5.12+ convention).

**Why.** Since 2.5.12 `query()` inspects the statement and blocks writes on a GET request, logging `query() called with a mutating statement; use exec()`. Code that worked on 2.5.11 silently stops writing on upgrade, and the only symptom is a log line. Splitting reads and writes at the call site also makes a future prepared-statement layer possible, because `exec()` never needs `$limit` / `$start`.

```php
// before
$db->query('DELETE FROM ' . $db->prefix('news') . ' WHERE storyid = ' . $id);

// after
$db->exec('DELETE FROM ' . $db->prefix('news') . ' WHERE storyid = ' . $id);
```

**Quick-fix** renames `query` to `exec` when the call has a single argument. With `$limit` / `$start` present the problem is still reported, but the fix is withheld: `exec()` takes one argument, so the extra ones must be removed by hand first. SQL held in a variable is not inspected; only a string literal as the first argument is read.

### 4.7 Raw superglobals — `XoopsSuperglobal`

Flags raw `$_GET`, `$_POST`, `$_REQUEST`, and `$_COOKIE` in module and XMF code. Prefer `\Xmf\Request`.

**Why.** `\Xmf\Request` does the type coercion, default handling and filtering that hand-written `isset($_POST['x']) ? (int) $_POST['x'] : 0` tends to get subtly wrong, and it makes the input source explicit. `$_REQUEST` merges GET, POST and COOKIE in `php.ini`-dependent order, so a cookie can override a form field; that is why the fix always names a source and never offers `$_REQUEST` as one.

```php
// before
$op   = $_GET['op'];
$name = $_POST['name'];

// after
$op   = \Xmf\Request::getString('op', '', 'GET');
$name = \Xmf\Request::getString('name', '', 'POST');
```

**Quick-fix scope.** Only a **keyed** read with a string-literal key (`$_GET['op']`, `$_POST['name']`, `$_COOKIE['sid']`) gets an Alt+Enter replacement, and it always uses `getString`. Change it to `getInt`, `getBool`, `getArray` or `getCmd` afterwards when the value is not free text. A bare `$_POST` (passed to a function, iterated, or used with a variable key) and any use of `$_REQUEST` are warnings only, because the right method and source cannot be inferred.

### 4.8 Missing registered template — `XoopsMissingRegisteredTemplate`

A template listed in `xoops_version.php` (`file` / `template` key) was not found under the module `templates/` (or `blocks/`) directory.

**Why.** On install and update XOOPS reads `$modversion['templates']` and copies each listed file into the `tplfile` table. A missing file is skipped without an error, so the page or block later fails with a Smarty "unable to read resource" message that names a template you registered but never created, or that was renamed on disk without the manifest following.

```php
$modversion['templates'][] = [
    'file'        => 'demo_index.tpl',   // must exist as templates/demo_index.tpl
    'description' => 'Index page',
];
```

Both template inspections read `xoops_version.php` only, so they cover legacy and hybrid modules; a `module.json`-only module is not checked.

**Quick-fix** creates the file under `templates/`, keeping any sub-path in the name (`blocks/demo_block.tpl` becomes `templates/blocks/demo_block.tpl`), with a Smarty comment `<{* name *}>` as placeholder so the manifest and disk agree; fill in the markup afterwards. The inspection runs on `xoops_version.php` and is the inverse of 4.9.

### 4.9 Unregistered template — `XoopsUnregisteredTemplate`

A `.tpl` file under the module `templates/` or `blocks/` directory is not listed in `xoops_version.php`, so Smarty cannot `display()` it as a registered module template. Theme overrides under `themes/` are ignored. The quick-fix appends a `$modversion['templates'][]` entry to the manifest.

**Why.** The `db:` template resource resolves through the `tplfile` table, and only registered templates get a row. An unregistered `.tpl` renders fine from the file system on a stock template set during development, then breaks on a site whose template set was imported, or on the first module update that resyncs templates. Registering it at creation time removes the surprise.

**Matching.** A manifest entry may be spelled relative to `templates/` (`demo_index.tpl`, the XOOPS convention) or from the module root (`templates/demo_index.tpl`); both are accepted. Names are compared case-insensitively.

**Quick-fix** appends this at the end of the manifest (before a trailing `?>` if there is one), unless the name is already quoted somewhere in the file:

```php
$modversion['templates'][] = [
    'file' => 'demo_orphan.tpl',
    'description' => '',
];
```

Files under `themes/` and `templates_c/` are never reported: a theme override is not a module template, and compiled templates are generated.

### 4.10 Wrong Smarty delimiters — `XoopsWrongSmartyDelimiter`

XOOPS Smarty uses `<{` and `}>` delimiters. Bare `{if}` / `{$var}` tags are usually a mistake and will not render.

**Why.** XOOPS configures Smarty with `<{` `}>` so that plain braces in CSS, JavaScript and JSON inside a template are left alone. A tag written in stock Smarty syntax is not an error: it is passed to the browser as literal text, so the symptom is `{$title}` appearing on the page, or an `{if}` block that always shows both branches.

```smarty
<!-- before -->
{if $items}<ul>{foreach $items as $item}<li>{$item.title}</li>{/foreach}</ul>{/if}

<!-- after -->
<{if $items}><ul><{foreach $items as $item}><li><{$item.title}></li><{/foreach}></ul><{/if}>
```

**What is matched.** A brace immediately followed by `$` or by one of the common tag keywords (`if`, `/if`, `foreach`, `/foreach`, `include`, `assign`, `block`, `literal`) up to the closing brace on the same line. Tags already written as `<{ … }>` are skipped, and so are braces that do not look like a tag, which keeps inline CSS and JavaScript quiet.

**Quick-fix** wraps the tag in `<{ }>`. Apply it per tag, or run **Code → Inspect Code** on the `templates/` folder and use **Apply fix** on the group to convert a whole template.

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

Type `_MI_` (or `_AM_`, `_MD_`, `_CO_`, `_MB_`) and **Ctrl+Space** — completions from every `language/**/*.php` `define()`. **Ctrl+B** (Go to Declaration) on a constant jumps to the `define()` (prefers `language/english/`).

## 7. New module

**Tools → XOOPS Support → New XOOPS Module Stub…** or **New → XOOPS Module…**

- **Legacy only** — `xoops_version.php`, `index.php`, language, template
- **Hybrid** — plus `composer.json`, `src/Service/…`, `config/`, `AGENTS.md`

## 8. Actions summary

| Action | Purpose |
| --- | --- |
| Show XOOPS Project Info | Dialog: Core Version, web root, modules |
| Refresh XOOPS Overview | Rescan + tool window |
| New XOOPS Module Stub… | Scaffold module |

## 9. Development (sandbox)

```powershell
.\gradlew.bat runIde
# other terminal after edits:
.\gradlew.bat buildPlugin --continuous
```

```bash
./gradlew runIde
# other terminal:
./gradlew buildPlugin --continuous
```

Focus sandbox to hot-reload (not under debugger).
