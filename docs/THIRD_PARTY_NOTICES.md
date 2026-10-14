# Third-party and visual-asset notes

The project license applies to project-authored material; it does not relicense dependencies or other owners' graphics/trademarks. Android is a trademark of Google LLC.

## Runtime app icons

Installed-app names/icons are obtained on the user's device through PackageManager. The repository does not bundle a collection of third-party social/video/chat app logos.

## Launcher resources

`ic_launcher_background.xml`, `ic_launcher_foreground.xml`, adaptive-icon XML and the ten density-specific launcher WebP files retain the recognizable Android Studio template/grid and Android robot artwork. They are not claimed as original TimeManager artwork. The raster launcher was visually inspected and the vector geometry reviewed; no unrelated app logo was found.

The Android robot graphic is derived from work created and shared by Google and is used under the [Creative Commons Attribution 3.0 license](https://creativecommons.org/licenses/by/3.0/), as described in [Google's Android brand guidelines](https://developer.android.com/distribute/marketing-tools/brand-guidelines). The template includes a white robot head, background and shadow. This credit does not imply endorsement or grant ownership of Google's marks. Keep this attribution when distributing those assets.

## UI vectors

All 18 drawable XML files were inspected; no bundled social/video app branding or unknown photographic asset was present.

- **Google Material icon paths/adaptations:** `ic_apps`, `ic_arrow_back`, `ic_close`, `ic_daily_limit`, `ic_home`, `ic_search`, `ic_session_pause`, `ic_settings`. The corresponding upstream apps, arrow_back, close, timer, home, search, pause and settings SVGs were fetched from Google's [Material Design icons repository](https://github.com/google/material-design-icons). Arrow-back/close/home paths match after formatting normalization; the other inherited paths use the corresponding conventional motifs with path-format/geometry differences. They are credited as inherited/adapted Material-style resources, not claimed as independently original artwork. Google makes Material icons available under Apache-2.0; preserve the upstream license/notices.
- **Simple project UI geometry:** `ic_bell`, `ic_chevron_right`, `ic_clock`, `ic_delete`, `ic_focus`, `ic_leaf`, `ic_statistics`, `ic_trend` are short, directly specified line/arc/bar symbols. No external branded asset is embedded in them. The two remaining drawables are the launcher template described above.

Review covered the checked-in XML/raster assets, not a broad legal certification or a claim that every historic generation step has a provenance record. No product resources were replaced during migration.

## Documentation imagery

The old AI-generated design contained real third-party app names/logos. It was removed from the **entire candidate reachable history**, and README no longer displays it. A built-in attempt to generate generic category icons/labels failed with a network error; no replacement image was accepted or distributed. The approved original remains only in the owner's private archive/local material. See [public UI asset policy](ui/README.md).

## Dependencies and project license

AndroidX/Compose, Room, DataStore, WorkManager, Kotlin/coroutines, KSP and Gradle/tooling retain their upstream licenses. Versions are in `gradle/libs.versions.toml` and wrapper configuration; preserve dependency notices when distributing binaries.

The standard Apache-2.0 text in `LICENSE` remains unchanged from [the Apache Software Foundation](https://www.apache.org/licenses/LICENSE-2.0.txt). `NOTICE` carries project copyright and visual-resource attribution separately. This source-release review does not establish store approval, production signing or a complete binary dependency-notice package.
