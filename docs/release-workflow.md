# Release Workflow (JDK 25)

## Versioning model
- Use SemVer tags: `vMAJOR.MINOR.PATCH`.
- Keep `main` releasable; all merges must pass CI (`mvn test` on JDK 25).

## Prepare a release
1. Ensure local tests pass:
   - `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -q test`
2. Update `CHANGELOG.md` under a new version section.
3. Create an annotated git tag:
   - `git tag -a v0.2.0 -m "DynamicLightEngine v0.2.0"`
4. Push branch and tags:
   - `git push origin main --follow-tags`

## Snapshot and local publish
- Build/install snapshots for local integration:
  - `mvn -DskipTests install`

## Release checklist
- API/SPI compatibility reviewed (`engine-api`, `engine-spi`).
- Both backends pass parity + lifecycle tests.
- Docs updated:
  - `README.md`
  - `docs/capabilities-compendium.md`
- No untracked artifacts intended for release.

## Optional: GitHub release notes
Use the changelog section for the tag as release notes body.
