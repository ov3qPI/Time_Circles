# Time Circles
Native Android astronomical clock built in Kotlin without Gradle, Jetpack Compose, or AndroidX. Package: `cc0.timecircles.naPHVO`.
The app displays longitudinal-day, synodic-month and solstice-year progress rings, supports passive GNSS and manually saved locations, and can use user-installed JPL planetary SPK ephemerides (DE440, DE442 and compatible imports) through Picante.
## Build in Termux
Keep the project ZIP in `/storage/emulated/0/Documents/zip/` and the standalone builder in `/storage/emulated/0/Documents/sh/`.
Run `bash /storage/emulated/0/Documents/sh/time_circles_native_build_v20261026T061342Z.sh`.
The builder uses Termux Kotlin, Java, AAPT2, Android SDK, D8, and APKSigner. If vendor JARs and D8 are missing from the ZIP, the first run downloads and embeds them into that same ZIP. Subsequent builds verify checksums and work offline. Source, classes, DEX, and downloaded temporary data are removed after every build. The resulting signed APK is copied to `/storage/emulated/0/Documents/apk/`.
The signing key is generated once privately in Termux at `$HOME/.android/time_circles_signing.jks`, never committed or bundled with published source. Preserve it to install later updates without uninstalling. Changing the application ID from `com.example.timecircles` to `cc0.timecircles.naPHVO` makes this a separate Android application; Android does not automatically transfer the prior application's private data.
## License
The original application source in this repository is dedicated under [CC0-1.0](LICENSE). Third-party dependencies and NAIF data remain under their respective terms and are not relicensed by this dedication. Vendored dependency binaries and signing keys are deliberately excluded from the GitHub repository. The project ZIP may contain verified vendored dependencies for offline builds.
## Project provenance
This is a native Kotlin reconstruction of the Time Circles Android application with Picante astronomy integration. Android runtime and astronomical behavior must be tested on device after changes.
