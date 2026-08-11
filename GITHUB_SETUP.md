# Publishing to GitHub

**Canonical repository:** [https://github.com/XOOPS/phpstorm-plugin](https://github.com/XOOPS/phpstorm-plugin)

This folder is a standalone tree for **XOOPS Support** (PhpStorm plugin), similar in layout to [php-hammer](https://github.com/hammer-tools/php-hammer).

## 1. Create the empty repo (if it does not exist yet)

- Organization: **XOOPS**
- Name: **phpstorm-plugin**
- Do **not** initialize with a README on GitHub if you will push this tree as the first commit.

## 2. First push

### Option A — uninitialized standalone tree (first-time upload)

Use this only if the directory is **not** already a git clone (no `.git` folder):

```bash
# Run from the root of the standalone project tree
git init
git branch -M main
git add .
git commit -m "XOOPS Support 1.0.0 Alpha 3 — PR review fixes"
git remote add origin https://github.com/XOOPS/phpstorm-plugin.git
# or: git remote add origin git@github.com:XOOPS/phpstorm-plugin.git
git push -u origin main
```

### Option B — existing clone

If you already cloned `XOOPS/phpstorm-plugin`, do **not** run `git init` / `git remote add`. From the clone root:

```bash
git remote -v
# if origin is wrong:
git remote set-url origin https://github.com/XOOPS/phpstorm-plugin.git
git add .
git commit -m "Your message"
git push -u origin main
```

If the remote already has commits you lack, `git pull --rebase` (or merge) before pushing.

## 3. CI

On push/PR to `main` or `master`, **`.github/workflows/gradle.yml`** runs:

```text
./gradlew check verifyPlugin buildPlugin
```

(Same task set as [php-hammer’s workflow](https://github.com/hammer-tools/php-hammer/blob/master/.github/workflows/gradle.yml).)

The built ZIP is uploaded as the **xoops-support-plugin** artifact.

First CI run downloads the PhpStorm SDK and Plugin Verifier — allow 10–20+ minutes.

## 4. Releases

Alpha tag:

```bash
git tag v1.0.0-alpha.3
git push origin v1.0.0-alpha.3
```

Tag **must** match `pluginVersion` in `gradle.properties` (release workflow enforces this).

**`.github/workflows/release.yml`** builds the plugin and attaches `build/distributions/*.zip` to a [GitHub Release](https://github.com/XOOPS/phpstorm-plugin/releases).

## 5. Optional Marketplace later

For JetBrains Marketplace:

1. Create a publisher account and obtain `PUBLISH_TOKEN`.
2. Add signing secrets if required (`CERTIFICATE_CHAIN`, `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD`).
3. Configure publishing in `build.gradle.kts`:

```kotlin
intellijPlatform {
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
```

Not wired by default.

## Layout checklist

| Path | Purpose |
| --- | --- |
| `.github/workflows/gradle.yml` | CI build + artifact |
| `.github/workflows/release.yml` | Tag → GitHub Release |
| `src/` | Plugin sources |
| `gradlew` / `gradlew.bat` | Wrapper |
| `LICENSE` | GPL-2.0 (verbatim; project is GPL-2.0-or-later) |
| `README.md` | Project home |
| `CONTRIBUTING.md` | Dev guide |
| `gradle.properties` | Version & platform |
