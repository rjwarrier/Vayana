# Baseline profile

`app/src/main/baseline-prof.txt` lists the classes and methods Vayana runs at launch and on its main paths
(library, opening and paging a book, notes, statistics). Release builds ship it, and `profileinstaller` compiles that
code ahead of time on first launch - including for APKs installed from GitHub releases, which the Play Store's cloud
profiles never reach. Launch and first scrolls are smoother as a result.

## Regenerating it

Do this after large changes to startup, the library or the reader.

1. Start an **emulator** (not a phone you read on: the run installs and uninstalls Vayana, which deletes its data).
   It needs a few hundred MB free on `/data`.
2. Run, with only that emulator selected:

   ```bash
   ANDROID_SERIAL=emulator-5554 ./gradlew :baselineprofile:connectedDebugAndroidTest
   ```

   `BaselineProfileGenerator` skips onboarding, imports the bundled sample book through "open with", reads a few
   pages, and visits Notes and Stats.
3. Copy the result into the app. Gradle pulls it off the emulator into
   `baselineprofile/build/outputs/connected_android_test_additional_output/debug/connected/<emulator>/`:

   ```bash
   cp "baselineprofile/build/outputs/connected_android_test_additional_output/debug/connected/"*/BaselineProfileGenerator_generate-startup-prof.txt app/src/main/baseline-prof.txt
   ```

The journey imports its sample book through a content provider in the test app; debug builds of Vayana declare it in
`app/src/debug/AndroidManifest.xml` (`<queries>`) so Android lets Vayana see it. Release builds don't have that entry.

The profile is recorded from the debug build: its class and method names are those of the code before R8, which
rewrites the profile for the minified release build.
