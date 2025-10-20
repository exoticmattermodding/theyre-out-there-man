# Repository Guidelines

## Project Structure & Module Organization
- Source code: `src/main/java/com/exoticmatter/totm/...` (entry: `TotmMod.java`).
- Resources: `src/main/resources` (e.g., `META-INF/mods.toml`, `assets/totm/...`).
- Generated resources: `src/generated/resources` (via the data run config).
- Dev runtime output: `run/` (logs, configs, saves). Do not commit.
- Build outputs: `build/` (final JAR in `build/libs`).

## Environment & Versions
- Java 17.
- Minecraft Forge `47.4.0` (MC 1.20.1 target).
- Use the included Gradle wrapper; no global Gradle required.

## Build, Test, and Development Commands
- Windows: `.\\gradlew <task>`; Unix: `./gradlew <task>`
- `gradlew build` — Compile, package, and reobfuscate (outputs to `build/libs`).
- `gradlew runClient` — Launch Minecraft client using `run/` as the working dir.
- `gradlew runServer` — Launch dedicated server.
- `gradlew runGameTestServer` — Execute Forge GameTests headless and exit.
- `gradlew clean` — Remove build artifacts.

## Coding Style & Naming Conventions
- Java 17; 4‑space indent; K&R braces; descriptive names.
- Packages under `com.exoticmatter.totm`; keep client‑only code in `client/`, registries in `registry/`.
- Classes `PascalCase`; methods/fields `camelCase`; constants `UPPER_SNAKE_CASE`.
- Resource/registry IDs: lowercase_with_underscores (mod id: `totm`).

## Testing Guidelines
- Prefer Forge GameTest for behavior; name classes `*Test`.
- JUnit tests (if used): `src/test/java`, e.g., `SomethingTest`.
- Run in‑game tests via `gradlew runGameTestServer`.
- Keep tests deterministic; cover entities, networking, registries.

## Commit & Pull Request Guidelines
- Use Conventional Commits: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`.
- Suggested scopes: `entity`, `item`, `network`, `client`, `registry`.
- PRs include: clear summary, linked issues, test steps (`runClient`/`runServer`), and screenshots/logs when visual.
- Update `gradle.properties` and `src/main/resources/META-INF/mods.toml` when changing mod metadata/version.
- Keep PRs focused; one logical change per PR.

## Security & Configuration Tips
- Do not commit `run/`, `build/`, crash reports, or local saves/worlds.
- Edit mod metadata only via `gradle.properties` and `META-INF/mods.toml`.
- Avoid absolute OS paths and secrets; put assets under `assets/totm/` with consistent names.
