# Third-party and visual-asset notes

The project license applies to project-authored material; it does not relicense dependencies, trademarks or other owners' work.

- AndroidX/Compose, Room, DataStore and WorkManager, Kotlin/coroutines, KSP and the Gradle wrapper/tooling retain their upstream licenses. Preserve upstream notices when distributing binaries. Dependency versions are in `gradle/libs.versions.toml` and wrapper configuration.
- Android Studio launcher templates and several simple vector symbols are present. The repository did not previously include per-asset provenance. A definitive source/license mapping for each visual asset has **not** been established in this cleanup; review it before binary distribution. No unsupported claim that every asset was independently authored is made.
- `docs/ui/2026-10-14-final-dark-ui.png` is an AI-generated, owner-approved illustrative design reference, reviewed for private usage data. It includes recognizable third-party app names/logos. They remain their owners' marks, are not licensed by this project's Apache grant and imply no affiliation. The image is not a raw phone screenshot.
- Runtime app icons are resolved from apps installed on the user's own device; those apps' icon assets are not bundled as a downloadable icon collection in this repository.

The standard Apache-2.0 text in `LICENSE` was obtained from [the Apache Software Foundation](https://www.apache.org/licenses/LICENSE-2.0.txt), unchanged. `NOTICE` carries the project's copyright separately.
