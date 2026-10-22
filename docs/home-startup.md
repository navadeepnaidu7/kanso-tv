# Home startup on the Bravia

On the tested Bravia VU31 (Android 14), Google TV's Home activity has intent priority 2 and Kanso has priority 0. The package resolver can still return Google TV even when the HOME role and preferred-activity record name Kanso. Verify both; a successful `set-home-activity` command alone does not prove the next Home intent will resolve to Kanso.

The **Customize → Home Screen** setting currently controls Kanso's accessibility override. It does not set Android's HOME role. The service handles Home presses and redirects stock Home activity events. At boot, stock Home can become visible before accessibility finishes binding, so its initial window event can be missed.

`KansoAccessibilityService.onServiceConnected` now checks the existing foreground window immediately and once after one second. It redirects only a known stock launcher package, leaving other foreground apps alone. Deferred callbacks are removed when the service is destroyed.

The fix was verified on the Bravia by disabling only Kanso's service, making Google TV the temporary default, opening Google TV, and rebinding Kanso's service without a Home press. Kanso reclaimed the existing window. All original accessibility services and the Kanso HOME preference were restored. This checks the missed-event scenario without claiming a full cold-boot test.

```powershell
adb -s <tv> shell cmd role get-role-holders --user 0 android.app.role.HOME
adb -s <tv> shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s <tv> shell cmd package set-home-activity --user 0 com.navadeep.kanso.tv
.\tools\check_home_startup.ps1 -Serial <tv>
```

The test wakes the TV first: an active screensaver otherwise prevents the intended Home window from being foreground. It restores the original service list and Home preference in a `finally` block.

The optimized release build and release vital lint passed. Full debug lint still reports three pre-existing `RestrictedApi` errors around `MainActivity.dispatchKeyEvent`; that method was not changed by this fix.
