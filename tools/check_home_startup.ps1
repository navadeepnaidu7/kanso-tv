param([Parameter(Mandatory = $true)][string]$Serial)
$ErrorActionPreference = 'Stop'

function Invoke-Tv {
    $result = & adb -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed" }
    return $result
}

$service = 'com.navadeep.kanso.tv/com.navadeep.kanso.tv.core.KansoAccessibilityService'
$savedServices = (Invoke-Tv shell settings get secure enabled_accessibility_services).Trim()
if ($service -notin $savedServices.Split(':')) { throw 'Enable Kanso accessibility before running this check.' }
$otherServices = ($savedServices.Split(':') | Where-Object { $_ -ne $service }) -join ':'
$savedHome = (Invoke-Tv shell cmd role get-role-holders --user 0 android.app.role.HOME).Trim()

try {
    Invoke-Tv shell input keyevent 224 | Out-Null
    if ($otherServices) { Invoke-Tv shell settings put secure enabled_accessibility_services $otherServices | Out-Null }
    else { Invoke-Tv shell settings delete secure enabled_accessibility_services | Out-Null }
    # Recreate this TV's original default while checking the accessibility fallback.
    Invoke-Tv shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx | Out-Null
    Start-Sleep -Milliseconds 1800
    Invoke-Tv shell am start -n com.google.android.apps.tv.launcherx/.home.HomeActivity | Out-Null
    Start-Sleep -Milliseconds 1200
    $before = Invoke-Tv shell dumpsys activity activities | Select-String 'topResumedActivity'
    if ($before -notmatch 'com.google.android.apps.tv.launcherx') { throw "Google TV was not in front before rebind; test is inconclusive: $before" }

    # No Home key or activity transition after rebind: reproduce a window visible before binding.
    Invoke-Tv shell settings put secure enabled_accessibility_services $savedServices | Out-Null
    $returned = $false
    for ($attempt = 0; $attempt -lt 12; $attempt++) {
        Start-Sleep -Milliseconds 300
        $current = Invoke-Tv shell dumpsys activity activities | Select-String 'topResumedActivity'
        if ($current -match 'com.navadeep.kanso.tv') { $returned = $true; break }
    }
    if (!$returned) { throw 'Kanso did not reclaim an already-visible Google TV Home after service binding.' }
    Write-Output 'Passed: Kanso reclaimed the existing Google TV window after service binding, without a Home press.'
} finally {
    if ($savedHome) { Invoke-Tv shell cmd package set-home-activity --user 0 $savedHome | Out-Null }
    Invoke-Tv shell settings put secure enabled_accessibility_services $savedServices | Out-Null
    Invoke-Tv shell am start -n com.navadeep.kanso.tv/.MainActivity | Out-Null
}
