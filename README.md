# Kanso for Google TV

A calm, minimal home screen (launcher) for Google TV and Android TV, built with Jetpack Compose for TV.

> **This fork was only possible because of [teja2495/my-toolkit-tv](https://github.com/teja2495/my-toolkit-tv)** by Teja Karlapudi.
> The original launcher (app loading, the app menu, hidden apps, the Accessibility-based Home button takeover and one-click force stop) is their work; this fork redesigns the home screen on top of it. Thank you.

## What this fork changes

- **Home view.** Opens on a full-screen wallpaper with the clock, and your favorites resting along the bottom. Press Down to bring up the dock; press Up (or Back) to return to the clean view.
- **Wallpapers.** Japanese woodblock prints by Hokusai and Hiroshige, photos of Earth from the International Space Station, both mixed, or your own photos (anything in Pictures or Downloads, e.g. sent with LocalSend). They crossfade every few minutes with a slow pan and zoom.
- **Weather.** A wallpaper mode showing the sky outside right now: the colors of the time of day, the sun or moon, clouds matching the real cloud cover and drifting on the real wind, and rain, snow, fog, lightning or stars when they're there. The conditions and temperature show under the clock. Previews of every kind of weather render with `./gradlew :app:testDebugUnitTest --tests '*WeatherPreview*'` (into `app/build/weather-previews`).
- **Favorites dock and All Apps.** Favorites sit in a dock; Down glides into a full-screen All Apps grid that shares the dock's columns, so Down always lands on the tile below.
- **Sorting.** Suggested (streaming and media first, then other apps, utilities and system), Name, Recently installed, or Custom: move any app anywhere from its menu.
- **Backgrounds.** While browsing, the background is a soft blur of the focused app's artwork.
- **Motion.** Spring-driven focus, scrolling and transitions, all tuned in one file (`ui/theme/Motion.kt`), and kept smooth on modest TV hardware: blurs and scrims are pre-rendered, so each frame is just a few image draws.
- **Opening apps.** The tile glides to the middle and grows while its colors flood the screen, then the app fades in; coming back plays it in reverse. Focused tiles get a fine light-catching edge.
- **Panels.** The app menu, Customize (wallpaper and sort) and Hidden apps slide in from the right.

## Getting started

1. Build and install the app on your TV (see below).
2. Try it from your apps list, or make it what the Home button opens. Once, from a computer:
   ```bash
   adb shell pm grant com.navadeep.kanso.tv android.permission.WRITE_SECURE_SETTINGS
   ```
   Then **Customize › Home Screen** switches between Kanso and the TV's own home (Google TV or Android TV) at any time. Kanso takes Home over with its accessibility service (which also makes Force stop one click); choosing the TV's home turns that off and goes there. Without the grant, the switch opens Accessibility settings to turn **Kanso** on or off by hand.

## Building

Open the project in Android Studio, or build from the command line:

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Release builds are signed with the local debug key (it's a personal sideloaded app). Use the release build for daily use; Compose is noticeably slower in debug builds. The project targets Android 9 (API 28) and later.

## Credits

- Original launcher: [teja2495/my-toolkit-tv](https://github.com/teja2495/my-toolkit-tv) by Teja Karlapudi.
- Woodblock prints: [The Metropolitan Museum of Art](https://www.metmuseum.org/about-the-met/policies-and-documents/open-access) Open Access collection (CC0).
- Weather: [Open-Meteo](https://open-meteo.com/) (CC BY 4.0); location from [ipwho.is](https://ipwho.is/) and [ipapi.co](https://ipapi.co/).
- Earth photography: [NASA Image and Video Library](https://images.nasa.gov/), courtesy of the International Space Station crews.

## License

[MIT](LICENSE).
