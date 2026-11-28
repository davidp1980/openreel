# Changelog

## [1.0.56] - 2026-11-28

### Fixed

- **Kids profile age-limit cover filtering (`CatalogRepositoryImpl`, `HomeExtrasSource`, `AgeRatings`):** enforce `profile.ageLimit` across featured billboards, Home/Movies/TV/Documentaries rows, category rows, trending, search, recommendations, and item/airing lookups so age-prohibited or unrated covers never appear in age-restricted kids profiles, tighten TMDB discover `certification.lte` to `G` below age 7, and key featured/home/recommendation caches by `ageLimit`.

## [1.0.55] - 2026-11-28

### Added

- **Documentaries section (`NavTab.DOCUMENTARIES`, `HomeFilter.DOCUMENTARIES`):** added a dedicated *Documentaries* (*Documentales*) top-bar section for both adult and kids profiles, with its own featured billboard and curated rows (Top 10, documentary movies, docuseries, true crime, history & society, music & culture, recent, acclaimed, plus age-filtered kids and family documentary rows).

## [1.0.54] - 2026-11-28

### Improved

- **Episode Center candidate filtering (`NewEpisodes`):** require either a watched episode or at least 30 seconds of watch progress (`WatchProgress.MIN_RESUME_MS`) before adding a show from history to Episode Center, preventing brief failed-stream or test plays from polluting the list.
- **Source picker marquee (`DetailsScreen`):** scroll long release names with `basicMarquee` while a source row is focused instead of truncating them with an ellipsis.

## [1.0.53] - 2026-11-23

### Changed

- Reverted the 1.0.52 Fire TV / Chromecast profile and video scaling changes to restore 1.0.51 performance and behavior.

## [1.0.52] - 2026-11-23

### Fixed

- **Video format scaling on Fire OS & Android TV (`VideoFitContainer`, `VideoFit`):** replaced `SurfaceView` view-transform `scaleX`/`scaleY` (ignored or clipped by Fire OS and several TV compositors) with real centered layout-bound resizing while keeping embedded subtitle padding inside the visible viewport.
- **Fast letterbox detection (`LetterboxDetector`):** switched from opening a second hardware `MediaCodec` + remote HTTP seeks to `PixelCopy` on the active `SurfaceView` with a predictable `4:3` (`1.333x`) fallback for 16:9 letterboxed frames when measurement is unavailable (e.g., DRM or Dolby Vision), tracked media transitions reactively, and reduced `TracksPanel` backdrop opacity to `0.72` so format changes are visible live.

### Added

- **Per-device optimization profiles (`DeviceProfile`, `PlaybackPolicy`):** added `ULTRA_CONSTRAINED`, `CONSTRAINED`, and `FULL` tiers for Amazon Fire TV (`AFT*` / `Amazon` 1–2 GB) and Chromecast with Google TV (HD `boreal` / 4K `sabrina`), using physical RAM (`ActivityManager.MemoryInfo.totalMem`) so `largeHeap` no longer triggers full-profile buffers or seek thumbnails on 1–2 GB sticks.
- **Platform-specific optimizations:** capped Coil disk cache by available storage (20% of free space, 32 MiB floor), disabled Android TV Watch Next channel/program syncing on Fire OS, shortened Fire TV display-mode switch timeout to `1.5 s`, and halved seek-thumbnail memory cache (`12 MiB`) on constrained devices.

## [1.0.51] - 2026-11-22

### Added

- **HDR priority setting (`HdrPreference`):** added *Automatic*, *Prefer SDR*, and *Ignore* options in *Settings → Sources & exclusions* so devices with HDR disabled can prefer SDR without hiding HDR-only releases.
- **Manual sort chip in Sources panel:** cycle between *Recommended*, *Size ↓*, *Size ↑*, and *Seeds ↓* directly from the source list header without affecting autoplay.

### Changed

- **Predictable base source sorting (`PrioritizeSourcesUseCase`):** removed the `languages.isNotEmpty()` boost, moved resolution (`quality.rank`) ahead of HDR, fixed `AUTOMATIC` HDR ranking when a display reports no HDR support, distinguished confirmed cached (`isCached`) and direct sources from unconfirmed `resolveUrl` links, and ordered cached sources by size before seeders.
- **Dual Castellano + VO & Latino audio detection (`SpanishAudio`):** ranked *Dual Castellano + VO* ahead of *Castellano* and *VO*, stopped bare `Dual` from scoring as Spanish, ensured explicit Latino indicators (including `es-la` and expanded Latin American country flags) rank below VO even when addons attach a Spain flag, and preserved focus/scroll position in `SourcesPanel` when slower providers finish loading.

## [1.0.50] - 2026-11-22

### Added

- **Per-profile & per-title track memory (`TrackMemoryRepository`):** manual audio and subtitle selections (including explicit subtitles off) are remembered per profile and title and reapplied automatically on subsequent episodes.
- **Aspect ratio modes (`VideoFit`) with hardcoded black-bar detection (`LetterboxDetector`):** cycle between *Original*, *Zoom (no bars)*, and *Stretch* from the player's track panel, including automatic detection and scaling for 2.39:1 letterbox bars baked into 16:9 remuxes.
- **Live subtitle appearance editor (`LiveSubtitleEditor`):** opening *Subtitles → Subtitle appearance* in the player now pauses playback and previews a live subtitle cue while adjusting size, bottom margin, text/background/outline colors, embedded style override, and reset.
- **Smart source failover by similarity (`SourceSimilarity`) & auto-switch toggle:** when a manually selected source fails and auto-switch is enabled, failover picks the closest source by language, resolution, HDR, audio codec, video codec, and size, showing a notice with the fallback source; disabling *Auto-switch if picked source fails* in Settings returns directly to the source list with the failure reason.
- **Persistent Home disk cache (`HomeDiskCache`):** stores the latest Home rows and featured billboard on disk for instant cold-start rendering with stale-while-revalidate background updates and offline fallback.
- **Real Home row pagination up to 100 items:** TMDB catalog rows fetch multiple pages and Trakt *Recommended for you* requests up to the configured *Items per home row* limit, reloading Home automatically when the setting changes.

## [1.0.48] - 2026-11-21

### Added

- **Watch history tab in My List:** added a *History* filter in `MyListScreen` and `MyListViewModel` showing watched movies and shows (grouped per show with the latest watched episode badge `S#:E#` / `T#:E#`), sortable by recent or A–Z.
- **Per-item and full history removal synced with Trakt:** long-pressing a title in History lets you remove it from watch history (`removeFromHistory`), and a *Clear history* control with confirmation clears all watched entries locally and on Trakt (`clearHistory` via `trakt.setWatched(..., watched = false)`), while leaving in-progress resume entries untouched.

## [1.0.47] - 2026-11-21

### Changed

- **Consistent D-pad focus and entry across all sections:** added deterministic `onEnter` focus targets in `HomeScreen` (`ROWS` restores the active card), `LiveTvScreen`, `MyListScreen`, `EpisodeCenterScreen`, `CalendarScreen`, and `SearchScreen` so pressing Down/OK from `TopNavBar` never drops focus onto secondary corner controls; `TopNavBar` now switches to Search on focus while keeping focus on the bar until confirmed; `Up` from `CalendarScreen`'s month bar or top poster row exits cleanly to `TopNavBar` (`CalendarFocusResult.ExitTop`), and `Back` from `HomeScreen` rows returns to the top and focuses `TopNavBar`.
- **Differentiated Home vs. Movies billboards and catalog rows:** split `HomeExtrasSource` featured curation so `HomeFilter.MOVIES` draws from theater/movie endpoints (`nowPlayingMovies`, `trendingMovies`, `topRatedMovies`) and excludes titles already featured on `HomeFilter.ALL`; reshaped `HomeFilter.ALL` rows into cross-media discovery (*Trending today* Top 10, *New releases*, mixed genre rows) while keeping `HomeFilter.MOVIES` focused on cinema (*Now in theaters*, *Top 10 movies this week*, *Classic movies*, and film genres).

### Added

- **Contextual "Because you watched …" rows:** added non-blocking `because_watched_*` recommendation rows in `HomeViewModel` powered by `catalogRepository.getRecommendations` from recent watch history and My List seeds, filtering out already-watched titles.

## [1.0.45] - 2026-11-21

### Changed

- **Switch sections on top nav focus:** moving across section pills in `TopNavBar` now displays each section immediately without requiring OK, while pressing OK or Down enters the section's content.
- **Consistent top nav focus across all tabs:** unified `MainScreen` onto a single persistent `TopNavBar` instance so switching to Live TV, Episodes, Calendar, or My List no longer unmounts the bar or steals focus, and guarded initial focus restoration in `LiveTvScreen`, `CalendarScreen`, and `MyListScreen` with `preferNavFocus`.

## [1.0.44] - 2026-11-21

### Changed

- **Back from Home rows focuses top navigation bar:** removed the `CatalogFeed` Back override so pressing Back while browsing rows bubbles up to `MainScreen`, revealing and focusing `TopNavBar` instead of jumping back to the featured hero (pressing Up from the first row still returns to the hero).

## [1.0.43] - 2026-11-21

### Changed

- **Land on the main featured hero on startup:** `initialHomeZone` now opens on the featured hero billboard when featured content is available instead of jumping straight into Continue Watching.
- **Fluid entrance scroll into Home sections:** sized the billboard to leave the first catalog row peeking at the bottom (`84.dp`), baked the featured slide scrims into the backdrop bitmap via `ScrimmedBackdropImage`, kept `HeroBackdrop` precomposed behind the billboard, and enabled synchronous focus transfer between the hero and row 0.
- **More compact row cards with `CardImage`:** reduced `PosterWidth` from `136.dp` to `120.dp` (and `PosterCaptionHeight` to `44.dp`), reduced `ContinueCardWidth` and `UpNextCardWidth` from `240.dp` to `208.dp`, and switched `ContinueCard` and `UpNextCard` from `AsyncImage` to `CardImage`.

### Removed

- **Home category chips:** removed the category chip bar from the Home billboard (and its profile toggle in `ProfileEditScreen`) so navigation moves directly from the hero into the catalog sections.

## [1.0.42] - 2026-11-21

### Changed

- **Native Android update installer UI:** switched self-updates from `PackageInstaller.Session` to `FileProvider` + `Intent.ACTION_VIEW` so Fire TV / Android TV keeps the native installation dialog open until completion and shows the native **Done** / **Open** buttons.
- **Automatic cleanup of cached update APKs:** deletes downloaded update APKs from `cacheDir/updates` upon `MY_PACKAGE_REPLACED` and when checking up-to-date versions.

## [1.0.41] - 2026-11-21

### Changed

- **Horizontal top navigation bar (`TopNavBar`):** replaced the left navigation rail and its translucent scrim with a clean horizontal top bar (profile switcher on the left, section pills in the center, Search and Settings on the right). On Home/Movies/TV it hides automatically when scrolling down into catalog rows, and on secondary tabs it sits cleanly above the content, freeing 100% of horizontal screen width.

## [1.0.40] - 2026-11-21

### Added

- **Import custom M3U playlist URL in Live TV:** added an **Import M3U** / **M3U playlist** action in the Live TV header that lets users configure, validate, and remove their own HTTP/HTTPS M3U/M3U8 playlist URL, automatically grouping channels by `group-title` alongside RTVE.

### Removed

- **Built-in third-party TDT and FAST feeds:** removed the hardcoded TDTChannels and FAST (Samsung TV Plus, Pluto TV, Plex TV) playlists so only RTVE is included out of the box.

## [1.0.39] - 2026-11-20

### Changed

- **Deprioritize Latino-only sources:** `SpanishAudio.score` now detects Latino/Latam/es-419/es-MX releases before generic Spanish/Dual keywords (unless Castellano or `ES` is also present), ranking Latino-only links below Castellano and English/VOSE instead of at the top.
- **Prefer Castilian Spanish (`es-ES`) audio tracks in ExoPlayer:** updated preferred audio languages to `es-ES` first and automatically pick the Castilian/European Spanish audio track over Latin American tracks when a file contains multiple Spanish tracks.

## [1.0.38] - 2026-11-20

### Changed

- **Try all eligible sources on playback failover:** removed the 8-attempt cap in `SourceFailoverController` and the 30-source queue cap in `DetailsViewModel` so playback keeps trying all available sources until one works (cancellable anytime with Back).
- **Trust filename ranking for Spanish-first profiles:** skipped mid-playback audio track probing and automatic source switching when **Spanish only** is off, relying on filename-based prioritization and ExoPlayer's preferred Spanish audio language selection (while keeping strict audio verification active for **Spanish only** profiles).

## [1.0.37] - 2026-11-20

### Fixed

- **Source selector badge legibility on focus:** adapted `SourceMediaBadges` (`4K`, `1080p`, `DV`, `HDR`, `HEVC`, `Atmos`, `CPU`) to use high-contrast dark text, icons, and borders when a source row is focused on its white highlight background.
- **Spanish audio filtering & untagged tracks:** disabling Kids mode in Profile Edit now clears `spanishOnlySources` instead of leaving it stuck on; **Spanish first** now appears ahead of **Spanish only**; **Show all** in the source picker now also reveals non-Spanish sources for manual selection; and Spanish-only verification no longer rejects untagged (`und`) audio tracks when the release explicitly advertises Castellano.

## [1.0.36] - 2026-11-20

### Added

- **FAST Live TV channels (Samsung TV Plus, Pluto TV, Plex TV):** integrated public Spanish FAST M3U playlists alongside RTVE and TDT, parsed concurrently with per-feed fault tolerance, ETag caching, cross-provider fallback URLs, and unified thematic sections (**Cine**, **Series**, **Entretenimiento**, **Documentales y Crimen**, **Deportes**, **Noticias**, **Infantil y Anime**, **Música**, and **Plex TV**).
- **Live TV section channel counts:** added subtle channel count badges next to every Live TV section header.

### Changed

- **Restored full TDT live directory:** removed the 1.0.35 channel caps and regional/international allowlists so all playable free-to-air channels from `tdtchannels.com` are available, organized into **TDT Nacionales**, **TDT Autonómicas**, and **TDT Internacionales**.

## [1.0.35] - 2026-11-20

### Changed

- **Curated Live TV TDT lineup:** narrowed the free-to-air TDT directory to three concise categories — **Nacionales** (up to 12), **Madrid** (up to 6), and **Internacionales** (up to 8 major channels) — keeping the entire Live TV lineup under 40 channels alongside RTVE.

## [1.0.34] - 2026-11-20

### Added

- **Free-to-air DTT (TDT) live TV directory:** integrated the public free-to-air Spanish & international TDT channel directory (linking directly to official broadcaster `m3u8` / `mpd` streams across national, regional, local, sports, news, children's, and thematic categories) alongside RTVE's live & 24/7 channels, with category filter chips in the Live TV tab.

### Fixed

- **Single-press `OK` in Live TV channel list:** opening the live action bar on `KeyUp` and removing the redundant `consumeInitialConfirmationKeyUp` guard from the channel list drawer ensures selecting a channel responds on the first `OK` press.

## [1.0.32] - 2026-11-20

### Fixed

- **Live TV overlapping audio / duplicate player sessions:** prevented `onStart()` from starting a second `ExoPlayer` before initial `start()`, always release any active player before creating a new one, stop playback immediately when leaving `PlayerScreen`, and ignore stale callbacks from replaced players.

## [1.0.31] - 2026-11-20

### Changed

- **Live TV quick bar (`OK`):** removed the Pause/Play button from the live zapping action bar and focused the Channels button first so pressing `OK` gives immediate access to the channel list.

## [1.0.30] - 2026-11-20

### Added

- **Live TV channel zapping:** press `▲` / `▼` (or hardware `CH+` / `CH-` keys) while watching Live TV to zap directly across playable channels with rapid-zap debouncing.
- **Live controls & channel drawer:** press `OK` during zapping playback to open the live action bar (Pause/Play, Channels list, Audio & subtitles, and Jump to live), or press `Guide` / `Channels` to jump directly via a side channel list drawer.
- **Last channel focus restore:** returning from the Live TV player restores focus and scroll position onto the channel you zapped to.

## [1.0.29] - 2026-11-20

### Added

- **Live TV (RTVE):** added a dedicated Live TV tab in the navigation rail with RTVE's live channels, active live events, and 24/7 thematic channels, loaded lazily when the tab is opened and hidden on Kids profiles.
- **Live playback & Widevine DRM:** added Media3 HLS, DASH, and on-demand Widevine DRM support with automatic non-DRM fallback, live-edge joining, DVR seek controls with jump-to-live (`▲`), and original-version / audio-description track labels.

### Improved

- **Navigation rail layout:** tightened vertical rail spacing and item height so all tabs, profile switcher, and Settings fit comfortably on 1080p TV screens.

## [1.0.28] - 2026-11-20

### Improved

- **Continuous Home vertical scrolling:** unified the Home billboard, category chips, and catalog rows into a single vertical scroll so moving down from the featured banner and categories transitions smoothly into the rows and compact header instead of jumping between separate views.

## [1.0.27] - 2026-11-20

### Changed

- **Panoramic 16:9 TV banner:** redesigned the TV launcher banner with a widescreen horizontal OpenReel mark and wordmark across all densities (`mdpi` through `xxxhdpi`) plus a `1280×720` Fire TV master asset.
- **Fire TV / Leanback launcher metadata:** declared `android:banner` and `android:logo` on both the application and `MainActivity` for better compatibility across TV launchers.

## [1.0.26] - 2026-11-20

### Changed

- **Complete OpenReel branding:** replaced the remaining internal themes, string resource IDs, Gradle project name, documentation, and screenshots with OpenReel branding.
- **New OpenReel artwork:** updated the app logo, Android TV banner, launcher icons, and phone configuration logo with a dedicated minimalist OpenReel mark.
- **Profile PIN hash prefix:** updated the PIN hash salt prefix to `openreel-pin:` (existing profile PINs need to be configured again).

## [1.0.25] - 2026-11-15

### Changed

- **OpenReel branding:** updated app name, artwork, and configuration to OpenReel while keeping `com.shieldstream.tv` compatibility.
- **Self-update repository:** pointed release checks and `OpenReel.apk` updates to `davidp1980/openreel`.

## [1.0.24] - 2026-11-14

### Added

- **Kids age limits:** Details shows TMDB age ratings, and Kids profiles can allow only all-ages titles or titles rated up to 7, 12, or 16. Blocked and unrated titles cannot start playback when a limit is set.
- **Profile restrictions and source size controls:** Kids profiles get a simpler catalog without actor pages or the Calendar. Profiles can override the interface and catalog language, and sources can be limited to 1, 2, 4, 8, or 20 GB.
- **Dialogue boost:** a per-profile Playback setting lifts speech frequencies and slightly reduces bass on supported decoded-audio outputs.

### Improved

- **Kids navigation:** the Settings icon no longer appears in the navigation rail for Kids profiles.

### Fixed

- **Profile language changes:** profile edits and selections are saved before an interface-language change restarts the app.

## [1.0.23] - 2026-11-14

### Added

- **More like this:** Details shows a rail of similar titles below the cast. It loads when you scroll to it, and Back returns focus to the title you opened.
- **Episode center actions:** hold OK on an episode in the episode center to mark it watched or unwatched, add the show to or remove it from My List, or dismiss it.

### Improved

- **Smoother Details scrolling:** moving up and down on Details renders more than twice as fast on low-power boxes such as the Nokia Streaming Box 8010 (median frame 48 → 20 ms).
- **Calendar ratings:** release ratings account for vote count, so titles with a handful of early votes no longer outrank established releases in rating filters.
- **Next episode:** the next-episode prompt during the credits always appears, and OK plays it. The episode starts automatically only when autoplay is enabled. The separate next-episode control in the playback controls was removed.

## [1.0.22] - 2026-11-12

### Added

- **Calendar:** a new tab shows your shows' episodes and the best-rated new movies and series by date. Select a day to see its posters, filter releases by rating (Any, 7+, 8+, 9+), and press OK to open Details.
- **Provider filters:** filter the source panel with All providers and individual provider chips. Provider names and audio-codec badges now appear on each source row.
- **Cast and actor pages:** Details shows the cast; select an actor to see their biography and filmography, and hold OK on a title there to add it to or remove it from My List.
- **Stremio My List import:** import missing movies and shows from the Trakt personal list in the profile editor. Existing entries stay unchanged.
- **Next episode in the player:** the playback controls show the next episode; press Up to focus it.
- **Release dates:** posters show the full release date instead of only the year.

### Improved

- **Smoother Home scrolling:** row navigation renders about twice as fast on low-power boxes such as the Nokia Streaming Box 8010.
- **My List loading:** saved catalog details are reused after a restart, so My List shows cached items at once. Under the Recent sort the first items appear before the rest of the list loads; other sorts no longer reshuffle when it finishes. Home's My list row shows its first 12 items sooner.
- **Faster poster grids:** My List and Home rows scroll more smoothly on low-power boxes, and posters already in memory appear in the first frame.
- **Fewer TMDB requests:** each show's details are downloaded once, and Hide completed no longer downloads the seasons of shows whose latest episode is unwatched.
- **Global source ranking:** sources from every provider share one ranked list. Language preferences and playback availability take priority, followed by decoding support, HDR, quality, seeders, and size.
- **Availability tiers:** cached and direct sources stay preferred, while non-cached torrents with zero seeds drop below seeded sources and sources with unknown seeder counts.
- **Source controls:** removed the redundant Spanish-only toggle; the profile preference still applies automatically.

### Fixed

- Keep focus on the first ranked source when opening the panel, changing providers, or receiving a new top-ranked result.
- On an actor page, pressing Up from the first row of the filmography scrolls back to the biography.

## [1.0.21] - 2026-11-11

### Added

- **My List item actions:** hold OK on a My List poster, either on Home or the full My List screen, to open Details, remove the item, or cancel.
- **Consistent action menus:** Continue Watching, Up Next, episodes, addons, and exit confirmation now use the same compact bottom action menu.

### Improved

- **Home catalog:** removed the redundant Trending today row from the All tab and corrected the Top 10 labels to describe their weekly TMDB data.
- **TV focus:** made initial focus and focus restoration more reliable when opening menus, switching views, or removing items.

### Fixed

- Prevent the OK release that opens a long-press menu from accidentally triggering its focused action.
- Preserve a useful focus destination after removing an item from My List.

## [1.0.20] - 2026-11-11

### Fixed

- **Missing logo:** the logo assets shipped in 1.0.19 were opaque near-black tiles, so they disappeared against the dark backgrounds of the phone configuration page, the setup screens and the profile picker. They are now transparent, so the mark shows wherever it is drawn.

## [1.0.19] - 2026-11-11

### Added

- **Per-episode watched state:** the Details action dock can now mark the selected episode as watched or unwatched, without playing it. Previously only a whole season could be toggled from the dock.
- **Episode options menu:** holding OK on an episode card opens a modal with Play episode, Mark as watched/unwatched, and Cancel. The hint under the episode row documents the gesture.

### Fixed

- Finishing a stream now always marks it watched, including sources whose container reports no duration.
- The Trakt badge on the profile picker is green instead of Trakt red, so a linked profile no longer reads as a warning.
- The mobile configuration page serves the current logo instead of the old mascot.

### Improved

- Updated the logo in the repository documentation to the current mark.

## [1.0.18] - 2026-11-10

### Added

- **Trakt ratings:** rate movies, overall shows, and selected episodes with a compact star picker.
- **Stremio addon manager:** add, replace, remove, and test multiple addons from Android TV or the mobile configuration page, with manifest-provided names and logos.
- **Compact action menus:** use the new bottom-dock style for Details actions and exit confirmation.

### Improved

- Show manifest URLs while typing in both addon editors.
- Preserve existing addon configuration and keep unchanged manifest values private during mobile updates.

### Fixed

- Restore D-pad focus after service-key validation errors.
- Always include the addon list in mobile configuration responses, including when it is empty.

## [1.0.17] - 2026-11-07

### Added

- **AllDebrid magnet playback:** use The Pirate Bay, Knaben, and Nyaa with AllDebrid. Upload magnets, check cached availability, select the requested movie or episode, and unlock the playback link directly.
- **Direct AllDebrid account validation:** verify API keys and premium status through AllDebrid instead of Torrentio.

### Improved

- Keep independent provider results available when another source provider fails.
- Wrap long service status messages without squeezing or truncating provider details.

## [1.0.16] - 2026-11-07

### Added

- **Android TV integration:** publish resumable movies and episodes to Play Next and a dedicated OpenReel Continue Watching channel, with profile-aware resume links and high-resolution landscape artwork.
- **Source exclusions:** filter sources by quality, video features, and advertised audio codecs. Use Show all in the source list for a temporary override.

### Improved

- Use the current OpenReel logo on profile, setup, and phone-configuration screens.
- Remove the redundant centered buffering indicator during playback.

## [1.0.15] - 2026-11-07

### Added

- **Navigation rail:** move the main destinations into a dedicated left rail for clearer TV navigation.
- **Backdrop controls:** optional soft backgrounds and high-resolution artwork settings for Home.

### Improved

- **App artwork:** new minimalist launcher icon and Android TV banner.
- **Profile avatars:** muted two-color gradients inspired by forest, sea, and other natural tones.
- **Visual consistency:** refined typography, icons, badges, cards, and surfaces across Home, Details, playback, setup, and Settings.
- **Home artwork:** centralized backdrop selection and added focused regression coverage.

### Fixed

- Preserve the selected source when resuming playback.

## [1.0.14] - 2026-11-06

### Improved

- **Monochrome look:** white is the only accent across Home, Details, the player, Settings, setup, and profiles. Red remains for new-episode badges and counts; colour is kept only for status, age ratings, ratings, and third-party brands.
- **Consistent shapes and surfaces:** one set of corner radii and neutral panels replaces the previous mix of radii and blue-tinted surfaces.
- **Top bar:** text tabs with a faint focus pill, an underline on the selected tab, and a round profile avatar.
- **Home billboard:** badge, title, details, a two-line synopsis, More info, and page dots in one left-aligned column.
- **Top 10 row:** reworked ranking numbers.
- **Source picker:** format, size, and languages on the main line; seeds and the release name on a dimmer second line. Long language lists are shortened, for example "EN/RU/IT +4".
- **Details:** season chips share one style, the watched count is easier to read, and episodes in progress show the remaining time.

### Fixed

- The player seek bar no longer shows as full before the video duration is known.

## [1.0.11] - 2026-11-05

### Added

- **App branding:** updated name and artwork throughout the app, Android TV launcher, setup, README, and release packages. Existing installations keep their application ID, profiles, preferences, and stored keys.
- **AllDebrid QR sign-in:** authorize AllDebrid from setup or Settings and save the returned API key automatically. Manual API-key entry remains available.
- **Guided phone setup:** complete first-run configuration in four steps—TMDB, debrid, optional SubDL, and language/audio/subtitles—with required-key validation and a final confirmation.
- **Phone configuration editor:** later, use Settings to edit service keys, source finders, and playback preferences from a phone or computer on the same local network. Sessions expire after 10 minutes and existing secrets stay hidden.

### Improved

- Use AllDebrid as the default debrid service for new installations.
- Updated release artifacts and self-update checks for the OpenReel repository and `OpenReel.apk` package.

### Fixed

- Restore D-pad navigation from the debrid API-key field back to the service selector.

## [1.0.10] - 2026-11-05

### Highlights

- **Automatic subtitle sync:** align downloaded subtitles with the dialogue track embedded in the video. The per-profile option is enabled by default and can be changed in Settings or during setup.
- **Clearer feedback:** a bilingual message shows when subtitle references are being read and synchronized.
- **Better playback on slower devices:** disabling synchronization skips demuxing but keeps automatic best-match subtitle downloads.

### Improved

- Select better reference tracks and dialogue cues, account for frame-rate differences, and require stronger matches before applying timing adjustments.
- Read fewer cues during startup and seek near the current playback position to reduce synchronization work.
- Support subtitle references in MKV, MP4, AVI, and MPEG-TS videos with bounded reads and consistent network handling.
- Choose matching disc parts more accurately, reject ambiguous multi-disc archives, and filter Portuguese releases mislabeled as Spanish.

### Fixed

- Keep the current subtitle and timing when a replacement cannot be synchronized.
- Prevent late searches, downloads, or synchronization results from affecting a different source or a playback session that has already ended.
- Report missing reference tracks immediately instead of downloading until timeout.
- Expanded regression coverage for subtitle synchronization, playback lifecycle, catalog handling, Home, and real MKV/MP4 media.

## [1.0.9] - 2026-11-05

### Added

- **Episode center:** browse recently released and upcoming episodes for up to 30 days, follow series from Details, dismiss releases individually, and see new-episode counts on posters and in the top navigation.
- **Episode notifications:** opt in per profile to checks every 12 hours for followed series. Watched and dismissed episodes are excluded, including progress synchronized from Trakt.
- **Continue Watching:** resume directly from the home row, with long-press actions for Details, Remove, and Cancel.
- **My List improvements:** sort by newest episodes, recently added, or title; hide completed series; and show new-episode badges.
- **Playback preferences in setup:** a fourth setup step configures interface language, preferred audio, and automatic subtitles. Language changes are deferred until setup finishes.
- **Per-profile subtitles:** prefer embedded Spanish or English subtitles, with automatic external-subtitle search when the selected language is unavailable.
- **Upcoming episode details and watched counters** on series pages.
- **Media-format badges:** inspect quality, HDR/Dolby Vision, video codecs, hardware/software decoding, audio formats, and subtitle formats in sources and playback.
- **Automatic device optimization:** adapt buffering, thumbnails, memory use, and source selection to detected device capabilities.

### Improved

- **TV performance:** lighter carousel transitions and focus animations, static full-screen artwork, bounded catalog concurrency, six-hour episode caches, and device-specific image caches and buffer defaults for the Nokia Streaming Box 8010.
- **Profiles:** expanded controls for avatars, kids mode, PIN protection, Trakt, Spanish-source preferences, and subtitle defaults.
- **Playback information:** clearer source status, Spanish-source filtering, and actual decoder information during playback.
- **Reliability tests:** added focused regression coverage for playback lifecycle, subtitles, notifications, season selection, Trakt concurrency, completion filtering, and setup preferences.

### Fixed

- Prevented stream resolution or player creation from starting playback after the activity stops, including duplicate players after a quick Home/return sequence.
- Preserved automatic and manual embedded-subtitle selection when recreating the player.
- Prevented changing seasons from reusing the previous season's episode or source list.
- Serialized Trakt token refreshes and scrobbles, prevented stale refresh failures from unlinking renewed accounts, and avoided repeatedly refreshing new 24-hour tokens.
- Kept episodes pending when notifications are disabled, posting fails, or the five-notification limit is reached instead of incorrectly marking them as delivered.
- Prevented series with failed season downloads from being incorrectly classified as completed and hidden from My List.
- Updated the setup completion summary to show the newly selected interface language before the deferred restart.
