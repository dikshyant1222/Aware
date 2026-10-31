# Aware — Detailed UI Design and Implementation Plan

Prepared October 25, 2026. This specification covers UI only: appearance, layout, visual hierarchy, reusable components, navigation presentation, interaction feedback, responsive behavior, and visual verification.

## 1. The target

Make the native Android Aware app look as close as practical to the health-insurance app shown in the supplied video. Recreate its design system, then use Aware's existing content and actions inside it.

The target is a pale blue/mint background, nearly white rounded cards, tinted circular icon wells, compact black typography, delicate shadows, fine white borders, generous section spacing, and a floating capsule dock. It should feel like the same designer created both apps.

The handwritten images override the reference when they specify an Aware-specific layout. The current repository supplies existing screens, text, data, and action callbacks. This phase changes their presentation. Detection algorithms, summary-trigger policy, microphone ownership, haptic patterns, audio processing, model behavior, service architecture, and storage are outside this UI plan.

This is a design/implementation specification, not a claim that the Android UI has already been changed. Dimensions, colors, fonts, and animation timings inferred from the video are reconstruction targets rather than original source values.

## 2. What was reviewed

- Branch: https://github.com/dikshyant1222/Aware/tree/android-native
- Reviewed commit: `bccfd2c56670e20d19af2cfb9e6407cfff72a702` — Add synchronized signing to live captions.
- Reference video: `tweeload_7ayps885.mp4`, approximately 14.72 seconds, containing Today, Consult, Backpain, Insurance, and Medical team screens.
- Notes: `20261025_065848.webp` and `20261025_065854.webp`, including their identical reattached copies.
- Main UI files: `HomeScreen.kt`, `HomeViewModel.kt`, `SettingsScreen.kt`, `PermissionScreen.kt`, `AslCaptionsScreen.kt`, `AvatarWebView.kt`, `CaptionBatcher.kt`, `CaptionsBubble.kt`, `AlertCard.kt`, `AlertPresentation.kt`, `SmartSummarySetting.kt`, and theme files.
- Embedded avatar entry: `app/src/main/assets/avatar/embed.html`.

The app is already Kotlin/Jetpack Compose. Continue in Compose and reuse the real existing avatar/caption screen. The UI redesign does not need a new app framework or a web replacement for the native screen.

## 3. Your handwritten requirements, translated into UI tasks

| ID | Requirement | UI treatment | Completion check |
|---|---|---|---|
| UI-01 | Hide Test Vibrations in ordinary use | Remove it from prominent Home/normal Settings presentation; retain an existing debug path if appropriate | No Test vibrations button in the normal UI |
| UI-02 | Settings becomes an icon | Circular gear button in the header | Tapping it opens existing Settings |
| UI-03 | Start/stop becomes a microphone icon | One large microphone control with a short status label | Separate full-width Start/Stop text buttons are gone |
| UI-04 | Listening indicator without levels | Soft ring/halo driven by existing listening state | No dB numbers, audio-level bars, floor/peak text, or numerical gauge |
| UI-05 | Remove What's happening; use an icon | Compact sparkle action wired to existing summary action | No large What's happening text CTA |
| UI-06 | Summary in a rounded treatment | White summary card beneath/overlapping the listening hero | Text is readable, with a small icon and quiet timestamp |
| UI-07 | Show fewer raw labels | At most four compact diagnostics rows; approximately two-second display refresh if needed | Display reduction is confined to presentation |
| UI-08 | Two Surroundings rows initially | Two real recent-sound rows by default | Zero/one rows stay zero/one; no fabricated filler |
| UI-09 | Show more, up to five maximum | Inline expansion to five total, then Show less | Never two plus five, and never an unlimited Home list |
| UI-10 | CC button for Live Talk | CC glyph in the floating capsule | One tap opens existing captions/avatar route |
| UI-11 | Avatar above live transcription | Large upper signer panel, text panel directly below | Both hands visible; captions remain large and synchronized |

Two handwriting interpretations are adopted so implementation can proceed: the raw-label line means approximately three to four labels per two-second display update, and the summary mark means a small sparkle/summary icon. Both are cheap presentation choices to adjust later.

The note about automatic summaries is retained as a product requirement, but this UI phase only displays summaries already emitted by the app and reuses its existing automatic behavior. Expanding when summaries are generated belongs to a later functional change; no new summary controller or trigger logic is part of this plan.

## 4. Current UI findings that affect the redesign

| Existing implementation | UI consequence |
|---|---|
| Dark background `#080A0C`, dark surface `#111418`, lime accent `#DAFF62` | A deliberate light palette is needed for the reference look |
| Many titles are 28–52 sp, body styles 20–22 sp | Introduce compact Home/Settings styles while preserving large caption/emergency text |
| Home is one heavily spaced LazyColumn | Break presentation into reusable visual sections |
| Home has text actions for vibration testing and Settings | Replace with requested icon presentation |
| Home shows numerical dB and a level meter | Remove these visible elements |
| AlertCard hardcodes dark surface, white text, dark chip | Restyle it directly; a global theme switch alone is insufficient |
| Captions screen hardcodes black backing and light text | Apply a coordinated light frame and readable text colors |
| Embedded HTML paints its body black | Change the backing color in the Android embed page too |
| Existing caption screen allocates 55% avatar / 45% text | Use this as a starting point, then tune visual proportions |
| Existing Home captions button is a 72 dp sign-language FAB | Replace the Home launcher with the requested CC entry |
| MainActivity requests light system icons for dark surfaces | Update status/navigation icon appearance for the light UI |
| Emergency screen uses shared theme typography | Preserve its explicit large styles when compacting normal UI |

The service/repository provide the data and actions. UI files should consume those existing interfaces; this task does not redesign them.

## 5. Reference design language

### Background

The screen starts with a pale icy blue at the very top, transitions to mint behind the header, then becomes pale gray/off-white. The gradient is gentle and low-saturation. It should not look like a vivid green wash.

Some reference screens have a white lower section beneath the gradient-backed upper region. Use that same layered treatment for Aware where it helps separate Surroundings or Settings groups.

### Surfaces

Cards are near-white with large rounded corners. Their thin borders are often white or extremely pale green/blue. Shadows are broad, faint, and soft. Circular buttons have a fine white rim that catches the eye more than their shadow.

The look is a light, softly raised interface. Strong black drop shadows, pronounced glass distortion, heavy bevels, or thick outlines would move it away from the reference.

### Typography

Titles are black, compact, and moderately bold. Supporting text is gray and usually one line in the small reference cards. The interface does not use huge all-caps labels, dense borders, or many competing font weights.

The exact font is not established by the video. Start with the current local/system sans-serif and tune through comparisons; a source-provided licensed font could be substituted later if available.

### Icons

The reference mixes black functional symbols with colorful flat icons and emoji-like illustrations. Reproduce that distinction: interface controls should be consistent vectors; sound illustrations may be fixed colorful assets inside tinted circles.

Avoid relying on live Unicode emoji for the final visual match, because artwork varies across operating systems. Fixed assets make size, style, and alignment predictable.

## 6. Reference-to-Aware mapping

| Reference component | Aware use | Match closely |
|---|---|---|
| Centered Consult/Insurance header | Centered Aware/Settings/Live Talk title | Position, font size, black weight |
| White round menu/profile buttons | Sparkle refresh, gear, back | White rim, circular shape, subtle lift |
| Semicircular Today gauge | Microphone listening-state hero | Arc geometry and softness; omit values/levels |
| Floating statistics panel | Summary preview card | Width, overlap, radius, border, shadow |
| Wellness recommendation cards | Recent sound/alert rows | Height, badge inset, text alignment, trailing control |
| Mind/Backpain grouped tiles | Grouped Settings panels | Gray outer shell, white inner surfaces |
| Floating bottom capsule | Home / CC / Summary / Settings dock | Proportions, active gray pill, inactive gray icons |
| Large exercise media card | Avatar viewport | Rounded prominence and consistent inset |
| Soft lower fade | Decorative layer behind dock | Gentle fade without obscuring important content |
| Smooth page settling | Native UI transitions | Low-amplitude fade/translation |

No leaf score, medical content, insurance wording, or artificial daily progress is introduced into Aware.

## 7. Reference canvas, units, and spacing

Use 390 × 844 logical units as the visual comparison artboard. In Android, use dp for geometry and sp for text. Actual status-bar/cutout/navigation insets determine placement. The video's iOS status bar, Dynamic Island, rounded device mask, and home indicator are presentation elements rather than Android app UI to reproduce.

| Element/token | Initial target |
|---|---|
| Main horizontal inset | 16 dp |
| Standard content width | Available width minus 32 dp |
| Wide-screen max content width | About 480 dp, centered |
| Standard card minimum height | 76 dp |
| Standard card radius | 20 dp |
| Large panel radius | 24 dp |
| Gap between sound rows | 12 dp |
| Gap between sections | 20–24 dp |
| Header row height | About 56 dp |
| Header visual circle | 44 dp |
| Icon well | 46 dp |
| Trailing visual circle | 36 dp |
| Functional icon size | 20–24 dp |
| Sound illustration size | 24–30 dp, optically centered |
| Minimum touch target | 48 × 48 dp |
| Floating dock | Approximately 286 × 58–60 dp |
| Dock bottom offset | Navigation inset plus 12–16 dp |
| Bottom scroll clearance | Dock + its offset + 24 dp |

A visible 36/44 dp circle can sit inside a larger touch target. Keep neighboring targets separate rather than expanding them into overlapping hit boxes.

All card heights are minimums. They must grow for large fonts or wrapped important messages. Do not force the entire expanded Home state into one non-scrolling screen.

## 8. Color specification

These are suggested reconstruction values, to be tuned against settled video frames.

| Token | Value | Purpose |
|---|---|---|
| SkyTop | `#DCEFF1` | Top gradient |
| MintTop | `#E7F3E9` | Header/hero wash |
| PaleTransition | `#EFF5EF` | Mid-background transition |
| Canvas | `#F5F5F5` | Lower background |
| Surface | `#FFFFFF` | Cards/panels |
| GroupSurface | `#F3F4F3` | Outer settings groups |
| Ink | `#171C20` | Titles and functional icons |
| SecondaryInk | `#666F70` | Supporting text |
| MutedInk | `#8A9393` | Nonessential metadata after contrast review |
| GreenAccent | `#50CC38` | Ring, decorative icons, small accents |
| GreenActionText | `#2F7D26` | Readable small green actions |
| CyanAccent | `#1DB9E9` | Communication/home accent |
| PinkAccent | `#EE4D96` | People/baby illustration accent |
| OrangeAccent | `#EE7547` | Traffic accent |
| PurpleAccent | `#A477D5` | Animals/secondary accent |
| EmergencyAccent | `#D63838` | Important warning treatment |
| SelectedDock | `#DEDEDE` | Active destination pill |

Starting gradient stops: sky at 0 dp, mint near 135 dp, pale transition near 235 dp, neutral canvas around 335 dp. The gradient should visually continue behind the header and hero instead of restarting inside each card.

Suggested badge gradients:

| Badge | Top | Bottom |
|---|---|---|
| Green | `#F5FCF3` | `#DDF8D7` |
| Cyan | `#F4FCFF` | `#DDF6FF` |
| Pink | `#FFF5F9` | `#FBD5E6` |
| Purple | `#FAF5FE` | `#EFE1FA` |
| Orange | `#FFF8F3` | `#FBE3D7` |

Keep the video-like bright green for decorative areas. Small actions use the darker green token so they remain readable on white. Measure text contrast after translucent backgrounds are composited.

## 9. Typography specification

| Role | Size / line height | Weight | Behavior |
|---|---|---|---|
| Page title | 18 / 24 sp | Semibold | Centered, normally one line |
| Section heading | 16 / 22 sp | Semibold | Left aligned |
| Card title | 16 / 21 sp | Semibold | Important text can wrap |
| Card supporting text | 14 / 20 sp | Normal | Preview may truncate |
| Metadata | 12–13 / 18 sp | Normal | Never used for essential instructions |
| Action link | 14 / 20 sp | Semibold | Dotted underline where appropriate |
| Dock active label | 14 / 20 sp | Semibold | Expand selected pill |
| Listening status | 22 / 28 sp | Semibold | Short state word/phrase |
| Summary sentence | 16 / 23 sp | Medium | Two-line preview, full sheet text |
| Final caption | 28 / 36 sp | Normal/Medium | Full text, readable wrapping |
| Translation | 24 / 32 sp | Normal | Below source text |

Keep large emergency typography explicit when changing the standard Material typography mapping. Compact Home text should never silently reduce the size of emergency content or live captions.

Use approximately 4–5 dp between a row's title and subtitle. Avoid excessive letter spacing. Compare actual letter shapes/weight before increasing font size to compensate for a font mismatch.

## 10. Shared visual components

### 10.1 Backdrop

One full-window background component paints the gradient. Scrollable content sits above it. Selected sections may add their own white lower background. It does not subscribe to audio levels or animate based on sound.

### 10.2 Circular action

- Visual circle: 44 dp for header, 36 dp for small trailing control.
- White translucent fill, thin white border, soft elevation.
- Black vector symbol, approximately 20–22 dp.
- 48 dp minimum touch area.
- Subtle pressed tint/scale, no large ripple spilling outside the circle.
- Meaningful content description.

### 10.3 Tinted icon well

- 46 dp circle with a pale vertical gradient.
- Fixed icon/illustration optically centered.
- Small soft shadow beneath, not a thick outline.
- Accent is determined by semantic sound category or the fixed asset treatment.
- Raw classifier score does not change the badge size or brightness.

### 10.4 Soft sound/action row

Horizontal composition: icon well; text column; trailing circle/control.

- Minimum height 76 dp.
- Approximately 15–16 dp horizontal padding.
- Approximately 14–16 dp gap from badge to text.
- Title 16 sp semibold; subtitle 14 sp gray.
- Near-white surface and 20 dp corners.
- Border about 1 dp, very pale accent/white.
- Trailing arrow only when the row opens detail. If the row replays a vibration, use a vibration symbol instead of a misleading navigation arrow.
- Treat the whole row as one action where possible; avoid duplicated accessibility labels for its decorative icon.

### 10.5 Section header

Left: 16 sp semibold title. Right: 14 sp green action with dotted underline. Align text baselines. Put sufficient invisible padding around a small action to make it easy to tap without changing its visual scale.

### 10.6 Grouped panel

Pale-gray outer container, 18–20 dp radius, small title/header area, and white inner rows or tiles. Use for Settings, echoing the Mind/Backpain groups in the reference.

### 10.7 Summary card

Near-white surface, 20–24 dp radius, small tinted sparkle well, compact text, quiet update age, optional trailing detail action. Its default height is approximately 88–104 dp but it grows for real content.

### 10.8 Floating capsule

Near-white outer pill, fine white border, soft shadow. Active route expands into a light-gray pill containing black icon and label. Inactive controls are gray and icon-only. Use actual route/modal state to select/highlight items; do not leave unrelated controls visually selected.

## 11. Home screen: full layout

The default order is:

1. Compact Aware header.
2. Microphone listening hero.
3. Summary card, gently overlapping the hero.
4. Latest alert card when an existing alert is available.
5. Surroundings title and two recent-sound rows.
6. Optional expanded rows, at most five total.
7. Debug presentation only in the development UI.
8. Floating dock over a subtle bottom fade.

### 11.1 Header

Center Aware at 18 sp semibold. Left: small round sparkle refresh control using the existing summary callback. Right: round gear opening Settings. The header should look like the reference's Consult header in proportions, with Aware-specific symbols.

Remove the large app-name block and the wide text actions beneath it. The header should feel light and balanced, with equal-sized left/right circles and a title centered independently of their content.

### 11.2 Microphone hero

Adapt the Today semicircle as a listening-state motif:

- Hero region around 140–150 dp tall.
- White arc approximately 280–290 dp across and 20–23 dp thick.
- Soft inside shading; rounded arc ends.
- Central mic icon approximately 28–32 dp within a 68–76 dp circular control.
- Status word such as Listening or Paused at about 22 sp.
- Active state uses a fixed green segment/halo, not a changing percentage.
- Paused state is neutral with no active pulse.
- Existing error/paused-alert signals get readable short text.

The mic uses the same existing toggle action. The visual change does not alter recording behavior. Do not infer a new calibrated sound level, progress value, safety score, or a new startup readiness signal.

The ring can gently pulse in opacity when the existing state is active. Its movement should look like a state indicator, never a fake responsive audio meter.

### 11.3 Summary preview

Place the card about 12–16 dp over the lower edge of the hero, echoing the floating statistics panel in the video.

- Left well: sparkles, 40–44 dp visual size.
- Small label: Summary.
- Main sentence: existing summary text, 16 sp, up to two lines in the Home preview.
- Optional small update age.
- Tap opens a full Summary sheet.
- If there is no summary, render a neutral empty state based on current listening state.
- Keep text visible while existing summary work proceeds; do not erase a useful sentence just to show a spinner.

UI copy examples, used only when appropriate to actual state:

| State | Display copy |
|---|---|
| Stopped, no summary | Start listening to recognize nearby sounds. |
| Running, no published summary | Recognized sounds will appear here. |
| Existing summary available | Display the app's exact published summary |
| Error state | Readable current error/fallback state; no invented generated sentence |

Changing the old What's happening control to a sparkle icon is within this phase. Making the summary generate under new conditions is deferred functional work.

### 11.4 Latest alert card

Preserve the existing important-alert/name-called presentation but make it visually compatible with the reference.

- Compact near-white card, 20 dp radius.
- Category icon well on the left.
- Real sound/name title and existing short message.
- Quiet last-heard time.
- Haptic-replay action remains wired to its current callback.
- Use pale red/accent border for emergency importance without turning every normal card into a warning.
- Move long secondary metadata into UI expansion/detail rather than filling Home with large blocks.
- Full important text must remain available and must not be irretrievably truncated.

Do not change which alert wins, when it fires, or which vibration plays. If the existing alert is historical, use Last alert/time wording rather than implying it is still continuously sounding.

The latest alert and a surroundings row may describe the same event. If both are shown, share consistent title/time and do not visually imply two separate events. A pure presentation deduplication can omit that same occurrence from the small surroundings preview when useful.

### 11.5 Surroundings

Title on the left: Surroundings. When there are more than two eligible existing rows, show Show more on the right with the reference's green dotted underline. Expanded label becomes Show less.

Each row shows:

- A fixed colored sound icon/illustration.
- User-facing sound name.
- Short last-heard age/descriptor.
- Relevant details or replay affordance.

Initially show at most two distinct recent sounds. Expanded: at most five total. Do not fill missing rows with sample data. An empty state uses one quiet white card with concise copy.

Use a display projection of the existing feed so repeated entries for the same semantic sound do not overwhelm the small preview. Keep name-called distinct from ordinary speech. This projection must not edit, truncate, or alter the underlying feed, engine inputs, or recognition events.

For this iteration, use timestamps to describe sounds as recently heard. A specific expiry/filter policy can follow the existing feed's semantics; introducing a new activity-detection algorithm is outside scope.

Expand inline with a smooth height change. Retain stable row keys and the user's position. When all five rows exist, scrolling is expected. Show less returns to the first two without jumping the whole page to the top.

### 11.6 Raw diagnostics

Presentation only: maximum four labels, compact name/score rows, no tall confidence bars, approximately one snapshot per two seconds if the panel is open. Keep this out of the ordinary Home layout.

A UI adapter can sample only label display data. Do not sample all Home state together, because alerts/captions should not inherit a display timer. Do not reduce the classifier's internal top-20 output. Do not add debounce that hides updates indefinitely under a continuous stream.

When the existing running state becomes false, clear/hide stale diagnostics immediately. This is display cleanup, not a new audio-control policy.

### 11.7 Bottom fade and scroll

A lower fade can reproduce the video's soft disappearance behind the dock. It sits above decorative/lower content and below the dock. Critical alert/caption information must remain readable, and every scrollable row must be able to move fully above the fade/dock.

Use sufficient bottom content padding. Do not let the final row become permanently obscured. The fade is noninteractive so it does not intercept taps.

## 12. Home layout budget

Use the following as a starting vertical budget inside the safe content area, then adjust for the actual device:

| Block | Approximate size/gap |
|---|---|
| Header | 56 dp |
| Header-to-hero gap | 16 dp |
| Hero | 144 dp |
| Summary | 88–104 dp, overlapping hero by 16 dp |
| Gap to alert | 20 dp |
| Alert, if present | 80–96 dp minimum |
| Gap to Surroundings | 20 dp |
| Surroundings header | 24 dp |
| Gap to rows | 12 dp |
| Two rows | 76 + 12 + 76 dp |
| Bottom clearance | Around 100 dp plus appropriate actual inset handling |

On a standard portrait screen, the quiet/two-row Home should look calm and relatively compact. Long summaries, large text, and expanded surroundings may scroll. Do not squeeze these states by reducing essential text size.

## 13. Dock specification

Preserve four positions from the reference, using real Aware actions:

| Position | Icon | Meaning | Target |
|---|---|---|---|
| 1 | House | Home | Existing Home route |
| 2 | CC | Live Talk | Existing captions route |
| 3 | Sparkles | Read Summary | UI-only summary sheet |
| 4 | Gear | Settings | Existing Settings route |

Home/Settings use the selected expanding pill. Summary is a modal action rather than a fictitious persistent tab; show its pressed/modal-open treatment and restore the route selection on dismissal. Hide the dock inside Live Talk so it cannot overlap the hands or transcription.

The Home header gear is a shortcut to the same Settings route. Use the same navigation callback and avoid stacking duplicates. Do not add a second independent settings flow.

Visual targets:

- Capsule around 286 dp wide, 58–60 dp high.
- Outer radius: capsule/half-height.
- Inner padding around 4 dp.
- Active pill about 50 dp high, width determined by icon/text.
- Active icon plus label gap about 6–8 dp.
- Inactive symbols about 20–22 dp.
- Soft shadow and fine white border.
- Position above actual Android navigation inset, not a hardcoded iPhone home-indicator position.

At large font scale, adapt label display rather than allowing overlap. Every icon retains a meaningful accessibility label even when text is visually hidden.

## 14. Summary sheet: UI only

This sheet makes the Home preview readable without introducing a new history/backend feature.

- White rounded top corners, approximately 24–28 dp.
- Small neutral drag handle.
- Title Summary at 18 sp semibold.
- Close control and optional existing refresh action.
- Full existing summary sentence, approximately 18–20 sp with comfortable line height.
- Existing last-updated time if available.
- Optional small context labels only if already available as real data.
- No model-selection form, developer statistics, or chat input in the reading surface.
- If no summary exists, show a neutral empty state rather than sample AI output.
- Dismiss through Back, outside tap, close, or normal sheet gesture.

Opening the sheet reads existing data. Its refresh control uses the existing summary request callback. UI work does not create automatic timers or change generation conditions.

## 15. Live Talk: detailed visual plan

### 15.1 Entry

Use the requested CC glyph for the Home entry. It opens the same existing avatar/caption route directly. Remove the competing Home sign-language FAB so the user has one clear entry.

### 15.2 Frame

- Same pale mint-to-white backdrop.
- Compact header: circular back control, centered Live Talk title, no dense metadata.
- Large signer panel immediately below.
- Live transcription directly beneath the signer.
- Hide the bottom capsule on this focused screen.
- Avoid a control row across the avatar or extra labels inside its animation area.

### 15.3 Avatar panel

- Width: available width minus 32 dp.
- Radius: about 24 dp.
- Surface: pale white/mint, coordinated with the rest of the app.
- Minimum useful height: around 300 dp on a normal phone; adapt to actual viewport.
- Starting proportion: current 55% signer/45% text. Test 58–62% signer if it improves hand visibility without starving captions.
- Match the reference's large media-card prominence, while using the existing interactive 3D model.
- Both hands, all fingertips, and the relevant face/upper body remain inside the viewport during representative gestures.
- No captions, navigation pills, chips, or spinners should cover the hands once ready.

The Android WebView is transparent, but the embedded page currently paints black. Change that page backing to transparent or a matching pale surface. Confirm lighting/body contrast visually. Do not redesign the model, rig, hand anatomy, sign vocabulary, or animation engine in this task.

### 15.4 Native/WebView boundary

Preserve the existing delayed page load until the WebView is measured, its resize notifications, local asset loading, bridge callbacks, and release cleanup. These mechanisms already support the current visible signer. A rounded wrapper must not reintroduce a zero-height or stale-aspect-ratio canvas.

Do not recreate/reload the WebView every time captions, timestamps, or unrelated Home data change. Apply styling around the existing instance and change the embedded backing color only where required.

### 15.5 Caption panel

- Final text: dark ink on white, 28 sp/36 sp.
- Partial text: readable medium gray.
- Current signed range: dark green with pale green highlight.
- Translation: 24 sp/32 sp below source text when present/different.
- Horizontal inset around 20–24 dp.
- Separation between transcript lines around 16–20 dp.
- No forced one-line truncation of the actual live transcription.
- Keep the existing text-follow/highlight behavior.

The code already synchronizes ranges to actual avatar playback. Recolor that presentation; do not replace it with timed guessed highlighting. Do not change batching, partial-word stability, queue order, or translation/sign replay behavior during the redesign.

### 15.6 Visible states

| Existing state | Visual treatment |
|---|---|
| Avatar loading | Small centered progress state in the signer panel |
| Ready, no transcript | Resting avatar; Waiting for speech… below |
| Partial recognition | Readable provisional text without excessive flashing |
| Final transcript | Dark stable text |
| Signing active | Existing accurate range highlight |
| Avatar failure | Small Retry state; usable text remains below |
| Speech error exposed by existing state | Concise readable error rather than an ambiguous blank panel |
| Back/navigation | Return to previous screen through existing action |

Render signals that already exist. Introducing new recognition recovery or microphone policies is deferred.

### 15.7 Compact/wide layouts

For narrow portrait: signer above text. For large font settings or a short window: permit panel growth/scrolling or adjust the split; do not shrink live text into tiny subtitles. For landscape/wide windows: an optional side-by-side visual arrangement can place signer left and text right, with real hand-span visibility checked.

## 16. Settings: detailed visual plan

Reorganize presentation of existing controls into reference-style grouped panels.

Proposed visual group order:

1. Sound categories.
2. Personal awareness/name settings.
3. Detection sensitivity.
4. Live Talk language/speech availability.
5. Smart summaries/model controls.
6. Device permission shortcuts.
7. Developer tools, when already appropriate for debug use.

### Header

Circular back action at left, centered Settings title. The page background uses the same top gradient. Avoid a large separate heading consuming a second header block.

### Group shells

Pale-gray rounded outer container, 18–20 dp corners. Dark 15–16 sp group title. White inner rows, small tinted category wells, consistent text inset. Use 12–16 dp between groups.

### Category rows

Retain existing category controls and values. Each row has an icon well, readable label, and switch. Minimum 60–64 dp height, expanding when necessary. Green selected treatment; no scores or raw classifier text in this screen.

### Name field

Rounded white field, visible persistent label, dark text, quiet placeholder. Keep enough room for real names. Align it to group insets. Present the existing name-detection switch as an adjacent normal setting row.

### Sensitivity

Retain existing control/values. Restyle track, thumb, labels, and panel. Use a readable short explanatory subtitle. Do not reinterpret the slider or change its thresholds.

### Language and speech status

Use a rounded selectable row opening the existing language menu. Current selection appears in dark text. Existing downloading/ready/error status appears as a compact subtitle or state row. Show real progress when available; do not invent progress values.

### Smart summaries

Restyle the current switch, installed-model status, import/remove buttons, progress indicator, and errors. Keep setup detail inside Settings so Home stays uncluttered. The model features and generation policy remain unchanged.

### Vibration testing

Remove the normal prominent Test vibrations row in accordance with the sketch. An existing debug-only diagnostic entry can remain outside the ordinary product presentation. Do not change haptic definitions or replay behavior.

## 17. Permission and emergency UI compatibility

### Permission entry

Use the same backdrop, circular microphone artwork, concise dark text, rounded white explanation card, and a clear permission action. Preserve existing grant/retry/open-settings actions. Recolor and arrange them; do not redesign permission eligibility rules.

### Emergency screen

Keep its deliberate large, high-contrast warning presentation. Shared normal-theme changes must not accidentally turn white emergency text unreadable or shrink emergency headings. Use explicit existing/emergency-specific type and color values when restructuring the theme.

This is a UI compatibility check, not a change to emergency triggers, flashing behavior, haptic pattern, notification permissions, timeout, or lock-screen mechanics.

## 18. Motion specification

| Element | Starting target | Visual constraints |
|---|---|---|
| Route entrance | 280–400 ms fade plus 16–28 dp settle | Soft, no pronounced spring |
| Closer video-style settling | Tune toward 450–650 ms if desired | Keep frequently used navigation comfortable |
| Dock selected pill | 220–300 ms | Smooth width and position, stable surrounding controls |
| Press feedback | Brief tint/elevation change, optional scale to 0.98 | No layout shift |
| Listening halo | Gentle 1.5–2 second opacity cycle | State-only, no fake loudness waveform |
| Summary text replacement | 150–220 ms crossfade | No blank interim card |
| Surroundings expansion | 200–260 ms height transition | Stable keys and scroll position |
| Important alert | Immediate readable content | Do not hold it back for decorative animation |
| Summary sheet | Standard calm modal motion | Back and close behavior remain clear |

Honor disabled animations. All states remain understandable through text/icons/colors without movement. The recording's large gray tap circles are demonstration pointers, not app UI assets.

## 19. Layering, insets, and native implementation details

Recommended visual order:

1. Full-window gradient.
2. Scrollable content.
3. Shared header where fixed.
4. Decorative bottom fade.
5. Floating dock.
6. Actual Android system UI.

Apply safe-area/inset padding once at the appropriate container. Avoid stacking root safe padding, Scaffold padding, and per-screen safe padding so the header/dock acquire double-sized margins.

For light surfaces, use dark system icons. Android's edge-to-edge behavior makes inset handling important; a screenshot-style hardcoded status-bar gap is insufficient across real devices.

Use Compose shapes, gradients, borders, Canvas arcs, and normal elevation for the first implementation. Heavy real-time backdrop blur is unnecessary for the reference look. A blur modifier on the whole card can blur its text rather than the background and should not be used as a shortcut.

Keep the project dependency versions pinned for this visual pass unless a specific UI API requires otherwise. The existing Compose stack is sufficient for cards, arcs, pills, sheets, transitions, and icon controls.

## 20. Proposed UI component/file changes

Paths below are relative to the repository root. New filenames are suggested; existing paths are exact.

| File | UI work |
|---|---|
| `ui/theme/Color.kt` | Light reference palette and readable text/accent tokens |
| `ui/theme/Type.kt` | Compact normal styles, explicit large caption/emergency styles |
| `ui/theme/Theme.kt` | Light scheme and consistent reference styling |
| `ui/theme/Shape.kt` (new, optional) | Shared rounded shapes |
| `MainActivity.kt` | Appropriate system-bar icon contrast and root visual/inset setup |
| `ui/components/AwareBackdrop.kt` (new) | Gradient background |
| `ui/components/AwareHeader.kt` (new) | Centered title and circular actions |
| `ui/components/SoftCircleButton.kt` (new) | White round action style |
| `ui/components/TintedIconWell.kt` (new) | Consistent colored badges |
| `ui/components/SoftActionCard.kt` (new) | Shared reference-style row |
| `ui/components/SectionHeader.kt` (new) | Section title and dotted action |
| `ui/components/AwareFloatingDock.kt` (new) | Four-slot capsule presentation |
| `ui/HomeScreen.kt` | Replace prototype composition with the planned layout |
| `ui/HomeViewModel.kt` | Presentation-only mapping/sampling if required; existing callbacks unchanged |
| `ui/HomePresentation.kt` (new, optional) | Pure UI models for preview and mapped row display |
| `ui/ListeningHero.kt` (new) | Arc, microphone, state label |
| `ui/SummaryCard.kt` / `ui/SummarySheet.kt` (new) | Existing summary preview/full view |
| `ui/SurroundingsSection.kt` (new) | Two/five rows and Show more/less |
| `ui/AlertCard.kt` | Light compact card while retaining message/replay callbacks |
| `ui/AlertPresentation.kt` | Theme-compatible icons/text presentation |
| `ui/SettingsScreen.kt` | Grouped light layout and icon controls |
| `ui/SmartSummarySetting.kt` | Restyle existing setup/status controls |
| `ui/CaptionsBubble.kt` | Retire/replace Home usage with one CC entry |
| `ui/AslCaptionsScreen.kt` | Light frame, readable text, larger signing viewport |
| `ui/AvatarWebView.kt` | Container integration only if necessary; retain bridge/lifecycle/measurement |
| `app/src/main/assets/avatar/embed.html` | Matching light/transparent backing |
| `ui/PermissionScreen.kt` | Entry styling and existing route presentation |
| `ui/EmergencyActivity.kt` | Only theme/type compatibility if shared styling affects it |
| `app/src/main/res/values/strings.xml` | Concise UI labels and icon accessibility descriptions |
| `app/src/main/res/drawable/*` | Fixed CC/mic/gear/sound assets as needed |

Kotlin paths in this table are under `app/src/main/java/com/aware/app/` unless a full repository-relative path is shown.

No changes are planned to `AwarePipeline`, `ListeningService`, `SituationController`, `SituationTrigger`, classifier/alert/haptic logic, speech controllers, model implementations, storage, or avatar animation mechanics.

The Android avatar entry is `app/src/main/assets/avatar/embed.html`. Styling only the standalone `avatar/dist/index.html` would not update the native screen. Preserve source/distribution relationships if a truly shared UI asset is edited.

## 21. UI implementation phases

### Phase A — Visual baseline

1. Capture the current Home, Settings, Live Talk, and emergency appearance.
2. Select settled frames from the health-app video for cards, headers, dock, and background.
3. Normalize the phone crop to the comparison artboard.
4. Inventory the existing icon assets and identify replacements needed for visual consistency.

Output: side-by-side component references and a fixed starting token sheet.

### Phase B — Theme and reusable pieces

1. Add light palette and compact normal typography.
2. Preserve explicit large caption/emergency styles.
3. Build backdrop, circles, badges, soft cards, section titles, and dock.
4. Render each with fixed preview data.
5. Tune geometry, then text, then surfaces/shadows.

Output: the reference design language is established before assembling full screens.

### Phase C — Home layout

1. Build the compact header.
2. Build the microphone ring/control.
3. Place the overlapping Summary card.
4. Restyle latest alert/name presentation.
5. Build two/five Surroundings and Show more/less.
6. Place the capsule and bottom fade.
7. Remove wide prototype buttons, visible levels, and the duplicate Home captions FAB.

Output: Home looks correct with stopped, quiet, alert, name-called, and expanded fixture states.

### Phase D — Existing-data binding

1. Bind the same existing start/stop/settings/captions/summary actions.
2. Render current real summary and alert data.
3. Map existing feed entries into the small surrounding-sound preview.
4. Apply display-only raw-label reduction if needed.
5. Ensure navigation/recomposition does not recreate the avatar or run data work unexpectedly.

Output: the new UI operates with current functionality, without new detection/service behavior.

### Phase E — Live Talk

1. Replace the dark outer viewing frame with the shared light design.
2. Lighten the embedded page backing.
3. Tune signer/text proportions with actual gestures.
4. Recolor final/partial/translated/highlighted text.
5. Preserve existing caption synchronization and WebView lifecycle behavior.

Output: a visibly larger, readable, uncluttered signer/transcription screen.

### Phase F — Settings and entry styling

1. Group existing settings into soft panels.
2. Restyle name, switches, slider, language selection, status, and model controls.
3. Remove normal vibration-test presentation.
4. Apply consistent permission entry styling.
5. Verify emergency remains legible after shared-theme changes.

Output: no leftover dark prototype panels or inconsistent control styles.

### Phase G — Visual and interaction QA

1. Compare real screenshots against reference component crops.
2. Check small widths, large text, actual insets, and keyboard behavior.
3. Check CC entry, Show more/less, modal dismissal, and back navigation.
4. Verify both hands stay within the signer frame.
5. Confirm existing captions, replay actions, and settings still work after visual changes.
6. Run build/lint and existing regressions affected by touched UI files.

Output: a reviewable UI change with screenshots and actual validation notes.

## 22. Visual QA checklist

### Reference fidelity

- [ ] Low-saturation sky/mint gradient matches the video's overall hue.
- [ ] Lower canvas is nearly white, without a stark mismatched background.
- [ ] Header centers and white-circle proportions match.
- [ ] Normal text sizes/weights feel compact like the reference.
- [ ] Repeated rows have one shared height/radius/padding rhythm.
- [ ] Colored badges have consistent optical size and position.
- [ ] Shadows are soft and restrained.
- [ ] Capsule selected pill is gray with black icon/text.
- [ ] Lower fade does not make important controls unreadable.
- [ ] System bars have correct icon contrast.
- [ ] No fake iOS device elements are drawn in Android.

### Your notes

- [ ] No prominent Test vibrations action.
- [ ] Header gear replaces Settings text button.
- [ ] One mic icon handles the existing toggle.
- [ ] No visible audio levels or dB display.
- [ ] No large What's happening CTA.
- [ ] Summary is a compact rounded visual component.
- [ ] Two surroundings initially, at most five after expansion.
- [ ] Raw diagnostics capped at four and visually quiet.
- [ ] One Home CC entry opens existing Live Talk directly.
- [ ] Avatar above readable live transcription.

### Interaction and usability

- [ ] Icon-only actions have meaningful labels.
- [ ] Visible small circles have non-overlapping 48 dp targets.
- [ ] Expanded rows can scroll above the dock.
- [ ] Large fonts grow cards rather than clipping text.
- [ ] Summary preview can open full text.
- [ ] Latest-alert replay still calls the existing replay action.
- [ ] Normal navigation does not create duplicate destinations.
- [ ] Back dismisses the summary sheet before leaving Home.
- [ ] Signer/loading/error states remain readable on the light backing.
- [ ] Hands are never obscured by controls.
- [ ] Caption highlight stays synchronized with the existing avatar callbacks.
- [ ] Emergency text remains large/high-contrast.

## 23. Device sizes and state fixtures

Use deterministic UI fixtures for previews and screenshot comparison. Keep fixture data out of production paths.

| Fixture | Purpose |
|---|---|
| Stopped, no events | Neutral hero and useful empty state |
| Listening, no summary yet | No fabricated generated output |
| Listening, two recent sounds | Primary Home composition |
| Listening, five expanded sounds | Expansion and bottom clearance |
| Long summary | Preview wrapping/full-sheet access |
| Doorbell alert | Compact alert and replay control |
| Name called | Distinct title/icon presentation |
| Emergency alert | Warning contrast and readable message |
| Avatar loading | Panel framing/progress alignment |
| Avatar ready, long transcript | Proportions and caption readability |
| Avatar failed | Retry layout without losing text |
| Translation present | Two-language text spacing |
| Settings import/download progress | Existing status layout |

Check widths 320, 360, 390, and 412 dp; font scales 1.0, 1.3, and 2.0; portrait/landscape; gesture and three-button navigation; keyboard open on the name field; and disabled animations.

## 24. How to compare the finished UI

1. Use settled video frames rather than half-faded transition frames.
2. Remove the surrounding white video presentation canvas from the comparison.
3. Compare header centers, card edges, badge centers, and dock outline first.
4. Compare font size, weight, line spacing, and truncation next.
5. Compare gradients, white borders, and shadow softness after geometry is correct.
6. Tune icon artwork/scale.
7. Compare route, dock, and expansion motion using screen recordings.

Use side-by-side crops or transparency overlays for equivalent components. A whole-screen pixel-difference metric between two apps with different content is not a useful sole target. Exact fonts/assets/source animation curves are unknown, so describe remaining differences honestly rather than promising a recovered pixel-identical original.

## 25. UI handoff brief

Recreate the health-insurance reference's visual system inside the existing Kotlin/Compose Aware app. Follow the two handwritten sketches for Home simplification and Live Talk framing. Build shared light-theme primitives first, then Home, the CC signer/transcription screen, and grouped Settings. Reuse existing data and callbacks. Limit raw-label reduction to presentation. Preserve large captions/emergencies and the current avatar bridge/measurement behavior. Finish with screenshot comparison and real interaction checks. Keep this change confined to the UI layer and its local assets.

## 26. Official UI references

- Android window insets: https://developer.android.com/develop/ui/compose/system/insets
- Compose accessibility defaults/minimum touch targets: https://developer.android.com/develop/ui/compose/accessibility/api-defaults

