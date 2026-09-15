# Agent notes for baharat

## Tests shared with Surveyor

[Surveyor](https://github.com/spice-labs-inc/surveyor) assembles the `spice` CLI and runs
black-box integration tests against it that reuse **this repository's own test data**. It
does not copy the data into its tree: `build-surveyor expectations` reads `test-fixtures.json`
at the root of this repository, at the exact commit the shipped jar was built from (the
commit recorded in `META-INF/git/<artifactId>.properties`), and exports what that file
declares. Every unit test here and every Surveyor case derived from it share one ID:

```
baharat/<qualified suite or class>#<test name>     code tests
baharat/<data path>[#<case id>]                    data-driven cases: the file's `id` field
```

Surveyor's `scripts/compare-test-ids.py` diffs those IDs against `tests/coverage/baharat.tsv`
and fails its CI on orphans, so keep these rules when you write or change tests:

- **Put expectations in data, not in code.** A new fixture-driven expectation belongs in a
  data file the unit test reads (see below for this repository's format); Surveyor can then
  evaluate the same file through `spice` without a second copy.
- **Every data file gets an `id`**, derived from its path exactly as the existing files do,
  and the unit test that reads it must require it. Never hand-pick ids.
- **Declare new data in `test-fixtures.json`**: an `expectations[]` entry (glob, format,
  `idField`, how the fixture path derives) and a `fixtures[]` entry with a tier (0 committed
  and small, 1 downloadable for a nightly run, 2 full corpora). Undeclared data is invisible
  to Surveyor.
- **Keep tier-0 fixtures small** (kilobytes to a few megabytes); anything large is a download
  (`downloads[]` with a sha256) or LFS, at tier 1 or 2.
- **Renaming a test or data file changes its ID.** That is allowed, but Surveyor's coverage
  manifest will report an orphan; mention it in the PR so the manifest is regenerated.
- **Keep the provenance plugin.** The jar must record its commit in
  `META-INF/git/<artifactId>.properties`; without it Surveyor falls back to the release tag.

### Here

- Expectations live in `src/test/resources/expected/<dir>.json`, one row per committed
  package, keyed by `id` = the package's path under `src/test/resources`. They are
  **generated**, not typed: after a reader change run

  ```sh
  mvn -q test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
      -Dexec.classpathScope=test -Dexec.mainClass=io.spicelabs.baharat.testdata.ExpectedPackagesGenerator
  ```

  and review the diff as a behaviour change. `ExpectedPackagesTest` checks every package
  against its row; a package the reader rejects is recorded with `error` rather than dropped.
- New test packages go under the existing format directories; the generator picks them up.
  Give a new package `tier: 0` only if it is small and hand-verified (the `TIER0` set in the
  generator lists the current ones); `openbsd/` and `pacman/` are tier 1 or 2 by size.
- Prefer asserting through the table in new tests (`ExpectedPackages.of(id)`) over inline
  literals, so the unit test and Surveyor's case cannot drift apart.
