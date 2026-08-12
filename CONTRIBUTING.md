# Contributing to XOOPS Support

Thanks for helping improve the PhpStorm plugin for XOOPS developers.

**Repository:** [https://github.com/XOOPS/phpstorm-plugin](https://github.com/XOOPS/phpstorm-plugin)

## Development setup

1. **JDK 21+** on `PATH` (or `JAVA_HOME`).
2. Clone [XOOPS/phpstorm-plugin](https://github.com/XOOPS/phpstorm-plugin) and open it as a Gradle project.
3. First build downloads the PhpStorm SDK (can take several minutes):

   ```bash
   ./gradlew buildPlugin
   ```

4. Sandbox IDE:

   ```bash
   ./gradlew runIde
   ```

## Coding notes

- Java 21, package root `org.xoops.support`.
- Keep inspections **fast and heuristic** unless a full PSI analysis is clearly worth it.
- Prefer **quick fixes** that are safe and local (single file / small edit).
- Do not hard-code AI vendor names or branding in UI strings.
- Index / PSI access from background threads must use `ReadAction` (see `XoopsProjectService`).

## Before opening a PR

```bash
./gradlew check verifyPlugin buildPlugin
```

CI runs the same command set.

## Version bumps

1. Update `pluginVersion` in `gradle.properties`.
2. Update `<version>` and change-notes in `src/main/resources/META-INF/plugin.xml`.
3. Update `CHANGELOG.md` and `whats-new.html`.
4. Tag release with the **same** version as `pluginVersion` in `gradle.properties`, e.g.  
   `git tag v1.0.0-alpha.1 && git push origin v1.0.0-alpha.1` (triggers release workflow).

## Reporting issues

Open an issue at [github.com/XOOPS/phpstorm-plugin/issues](https://github.com/XOOPS/phpstorm-plugin/issues).

Include PhpStorm version, plugin version, and a minimal reproduction (path under `modules/` when relevant).
