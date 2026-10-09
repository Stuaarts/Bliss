#Requires -Version 7.0
param(
    [ValidateSet('authentication', 'marketplace', 'secondary', 'connectivity')]
    [string]$Scenario = 'authentication',
    [ValidateSet('all', 'request', 'quote', 'accept', 'fulfil', 'review')]
    [string]$MarketplacePhase = 'all',
    [ValidateSet('all', 'cancel', 'create', 'fulfil', 'file', 'evidence', 'resolve', 'check')]
    [string]$SecondaryPhase = 'all',
    [ValidatePattern('^[a-z0-9-]{1,40}$')]
    [string]$RunId = ('run-' + (Get-Date -Format 'yyyyMMdd-HHmmss')),
    [string]$DeviceId = 'emulator-5554',
    [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$adb = Join-Path $AndroidSdk 'platform-tools/adb.exe'
$flutter = Join-Path $root '.tools/flutter/bin/flutter.bat'
if (-not (Test-Path -LiteralPath $flutter)) { $flutter = (Get-Command flutter).Source }
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
$env:ANDROID_HOME = $AndroidSdk
$results = Join-Path $root $(if ($Scenario -eq 'marketplace') { ".work/native-marketplace/$RunId-$MarketplacePhase" }
    elseif ($Scenario -eq 'secondary') { ".work/native-secondary/$RunId-$SecondaryPhase" }
    elseif ($Scenario -eq 'connectivity') { ".work/native-connectivity/$RunId" } else { '.work/native-oidc' })
$inputPath = Join-Path $results 'accounts.json'
$deviceInput = '/data/local/tmp/bliss-native-auth.json'
if ($Scenario -ne 'marketplace' -and $MarketplacePhase -ne 'all') {
    throw 'MarketplacePhase applies only to the marketplace scenario.'
}
if ($Scenario -ne 'secondary' -and $SecondaryPhase -ne 'all') { throw 'SecondaryPhase applies only to the secondary scenario.' }

function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed: $Program" }
}

function Invoke-Instrumentation([string[]]$Arguments, [int]$Timeout, [string]$LogName, [string]$Checkpoint) {
    $logPath = Join-Path $results "$LogName.log"
    $process = Start-Process -FilePath $adb -WindowStyle Hidden -PassThru -ArgumentList $Arguments `
        -RedirectStandardOutput $logPath -RedirectStandardError (Join-Path $results "$LogName-errors.log")
    if (-not $process.WaitForExit($Timeout)) {
        $process.Kill()
        & $adb -s $DeviceId shell am force-stop com.bliss.native_smoke
        throw 'Native check exceeded its time limit; inspect the ignored logs.'
    }
    $log = Get-Content -LiteralPath $logPath -Raw
    # Android instrumentation may return exit 0 even when an assertion fails.
    if ($process.ExitCode -ne 0 -or $log -notmatch 'OK \(1 test\)' -or $log -notmatch $Checkpoint) {
        throw "Native check failed. Inspect $logPath (kept local)."
    }
}

# This driver stops both Bliss and Chrome; never run it on a personal device.
if ($DeviceId -notmatch '^emulator-[0-9]+$') { throw 'A dedicated Bliss_Test emulator is required.' }
$avd = & $adb -s $DeviceId emu avd name
if ($LASTEXITCODE -ne 0 -or $avd[0].Trim() -ne 'Bliss_Test') { throw 'Start the dedicated Bliss_Test AVD first.' }
$boot = & $adb -s $DeviceId shell getprop sys.boot_completed
if ($LASTEXITCODE -ne 0 -or "$boot".Trim() -ne '1') { throw 'Wait for emulator boot to complete.' }
if ((Invoke-RestMethod 'http://localhost:8088/q/health/ready' -TimeoutSec 15).status -ne 'UP') {
    throw 'Start the local Bliss API first.'
}
$discovery = Invoke-RestMethod 'http://localhost:8180/realms/bliss/.well-known/openid-configuration' -TimeoutSec 15
if ($discovery.issuer -ne 'http://localhost:8180/realms/bliss') { throw 'Unexpected local issuer.' }

Push-Location $root
try {
    New-Item -ItemType Directory -Path $results -Force | Out-Null
    if ($Scenario -ne 'connectivity') {
        $accounts = Get-Content '.work/demo-accounts.json' -Raw | ConvertFrom-Json
        # Profiles can be renamed in the demo. Read only expected demo display names.
        $sql = "SELECT coalesce(json_object_agg(oidc_subject, display_name), '{}'::json) FROM bliss_user WHERE oidc_subject IN ('50000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000004');"
        $profileJson = $sql | & docker compose exec -T postgres sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'
        if ($LASTEXITCODE -ne 0) { throw 'Could not read local demo profile names.' }
        $profiles = $profileJson | ConvertFrom-Json -AsHashtable
        if ($Scenario -eq 'marketplace' -and $MarketplacePhase -in @('all', 'request')) {
            # RunId has a strict allowlist; prevent a repeat from creating ambiguous duplicates.
            $existsSql = "SELECT EXISTS (SELECT 1 FROM service_request WHERE title = 'Android garden $RunId');"
            $exists = $existsSql | & docker compose exec -T postgres sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'
            if ($LASTEXITCODE -ne 0) { throw 'Could not check the local marketplace run ID.' }
            if ("$exists".Trim() -ne 'f') { throw 'This run ID already has a request. Inspect it and resume a specific phase, or use a fresh run ID.' }
        }
        $fixture = @{}
        if ($Scenario -eq 'secondary') {
            if ($SecondaryPhase -in @('all', 'cancel', 'create')) {
                $existsSql = "SELECT EXISTS (SELECT 1 FROM booking WHERE city IN ('Demo $RunId cancel', 'Demo $RunId dispute'));"
                if ($SecondaryPhase -ne 'all') {
                    $suffix = if ($SecondaryPhase -eq 'cancel') { 'cancel' } else { 'dispute' }
                    $existsSql = "SELECT EXISTS (SELECT 1 FROM booking WHERE city = 'Demo $RunId $suffix');"
                }
                $exists = $existsSql | & docker compose exec -T postgres sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'
                if ($LASTEXITCODE -ne 0) { throw 'Could not check the secondary run ID.' }
                if ("$exists".Trim() -ne 'f') { throw 'This run ID already has a booking. Inspect it before choosing a diagnostic phase or a fresh run ID.' }
            }
            $offeringSql = "SELECT json_build_object('id',o.id,'title',o.title,'price',o.price::text,'currency',o.currency,'category',c.name) FROM service_offering o JOIN service_category c ON c.id=o.category_id JOIN provider_member m ON m.provider_id=o.provider_id JOIN bliss_user u ON u.id=m.user_id WHERE u.oidc_subject='50000000-0000-0000-0000-000000000002' AND m.member_role='OWNER' AND o.active ORDER BY o.title,o.id LIMIT 1;"
            $offeringJson = $offeringSql | & docker compose exec -T postgres sh -c 'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At'
            if ($LASTEXITCODE -ne 0 -or -not $offeringJson) { throw 'Publish an offering owned by the generated provider before this check.' }
            $fixture['offering'] = $offeringJson | ConvertFrom-Json -AsHashtable
        }
        $names = if ($Scenario -eq 'secondary') { @('customer', 'provider', 'admin') } else { @('customer', 'provider') }
        foreach ($name in $names) {
            $account = @($accounts | Where-Object username -eq $name)
            if ($account.Count -ne 1 -or -not $account[0].password) { throw "Missing generated demo identity: $name" }
            $subject = switch ($name) {
                'customer' { '50000000-0000-0000-0000-000000000001' }
                'provider' { '50000000-0000-0000-0000-000000000002' }
                'admin' { '50000000-0000-0000-0000-000000000004' }
            }
            if (-not $profiles[$subject]) { throw "Initialize the local $name profile before running this check." }
            $fixture[$name] = @{ username = $name; password = $account[0].password; displayName = $profiles[$subject] }
        }
    }

    Push-Location 'apps/mobile'
    try {
        # Flutter generates the ignored Gradle wrapper on a clean checkout.
        Invoke-Checked $flutter @('build', 'apk', '--debug')
        Push-Location 'android'
        try {
            Invoke-Checked './gradlew.bat' @(':native_smoke:assembleDebug', ':native_smoke:assembleDebugAndroidTest')
        } finally { Pop-Location }
    } finally { Pop-Location }
    foreach ($apk in @(
        'apps/mobile/build/app/outputs/flutter-apk/app-debug.apk',
        'apps/mobile/build/native_smoke/outputs/apk/debug/native_smoke-debug.apk',
        'apps/mobile/build/native_smoke/outputs/apk/androidTest/debug/native_smoke-debug-androidTest.apk'
    )) { Invoke-Checked $adb @('-s', $DeviceId, 'install', '-r', $apk) }
    foreach ($port in @('8088', '8180')) {
        Invoke-Checked $adb @('-s', $DeviceId, 'reverse', "tcp:$port", "tcp:$port")
    }
    if ($Scenario -eq 'connectivity') {
        $prefix = @('-s', $DeviceId, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'blissRun', 'connectivity', '-e', 'class')
        $runner = 'com.bliss.native_smoke.test/androidx.test.runner.AndroidJUnitRunner'
        try {
            Invoke-Checked $adb @('-s', $DeviceId, 'reverse', '--remove', 'tcp:8088')
            Invoke-Instrumentation ($prefix + @('com.bliss.native_smoke.NativeConnectivityTest#unavailable', $runner)) `
                120000 'unavailable' 'Native catalog reports connection failure'
        } finally {
            # Restore connectivity even when the assertion or instrumentation fails.
            Invoke-Checked $adb @('-s', $DeviceId, 'reverse', 'tcp:8088', 'tcp:8088')
        }
        Invoke-Instrumentation ($prefix + @('com.bliss.native_smoke.NativeConnectivityTest#recover', $runner)) `
            120000 'recovered' 'Native catalog recovered through Try again without app restart'
        foreach ($name in @('android-api-unavailable.png', 'android-api-recovered.png')) {
            Invoke-Checked $adb @('-s', $DeviceId, 'pull', "/sdcard/Android/data/com.bliss.native_smoke/files/$name", (Join-Path $results $name))
        }
        Write-Output "PASS: native catalog outage and retry. Backend unchanged; API reverse mapping restored. Results: $results"
        return
    }
    try {
        $fixture | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $inputPath -Encoding utf8NoBOM
        Invoke-Checked $adb @('-s', $DeviceId, 'push', $inputPath, $deviceInput)
        Invoke-Checked $adb @('-s', $DeviceId, 'shell', 'chmod', '600', $deviceInput)
        $instrumentation = @('-s', $DeviceId, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'blissRun')
        if ($Scenario -eq 'marketplace') {
            $instrumentation += @('marketplace', '-e', 'runId', $RunId, '-e', 'marketplacePhase', $MarketplacePhase,
                '-e', 'class', 'com.bliss.native_smoke.NativeMarketplaceTest')
        } elseif ($Scenario -eq 'secondary') {
            $instrumentation += @('secondary', '-e', 'runId', $RunId, '-e', 'secondaryPhase', $SecondaryPhase,
                '-e', 'class', 'com.bliss.native_smoke.NativeSecondaryFlowsTest')
        } else {
            $instrumentation += @('local-demo', '-e', 'class', 'com.bliss.native_smoke.NativeOidcTest')
        }
        $instrumentation += 'com.bliss.native_smoke.test/androidx.test.runner.AndroidJUnitRunner'
        $timeout = switch ($Scenario) { 'marketplace' { 600000 } 'secondary' { 900000 } default { 240000 } }
        $checkpoint = if ($Scenario -eq 'authentication') { 'Switch back to customer after identity-provider logout' }
            elseif ($Scenario -eq 'marketplace' -and $MarketplacePhase -eq 'all') { 'Native marketplace lifecycle finished; signed out' }
            elseif ($Scenario -eq 'secondary' -and $SecondaryPhase -eq 'all') { 'Native secondary flows finished; decision persisted and signed out' }
            else { 'Verified: Native' }
        Invoke-Instrumentation $instrumentation $timeout 'instrumentation' $checkpoint
        $screenshots = if ($Scenario -eq 'authentication') {
            @('android-customer-restored.png', 'android-provider-signed-in.png', 'android-signed-out.png')
        } elseif ($Scenario -eq 'marketplace' -and $MarketplacePhase -eq 'all') {
            @('android-marketplace-request.png', 'android-marketplace-chat.png', 'android-marketplace-provider-complete.png',
                'android-marketplace-completed.png', 'android-marketplace-review.png')
        } elseif ($Scenario -eq 'secondary' -and $SecondaryPhase -eq 'all') {
            @('android-direct-cancelled.png', 'android-dispute-opened.png', 'android-dispute-provider.png',
                'android-dispute-resolved.png', 'android-dispute-voided.png')
        } else { @() }
        foreach ($name in $screenshots) {
            Invoke-Checked $adb @('-s', $DeviceId, 'pull', "/sdcard/Android/data/com.bliss.native_smoke/files/$name", (Join-Path $results $name))
        }
        $phase = if ($Scenario -eq 'secondary') { $SecondaryPhase } else { $MarketplacePhase }
        Write-Output "PASS: $Scenario ($phase). See instrumentation checkpoints for verified actions."
        Write-Output "Logs and credential-free app screenshots: $results"
    } finally {
        & $adb -s $DeviceId shell rm -f $deviceInput
        if ($LASTEXITCODE -ne 0) { Write-Warning 'Device input cleanup failed; remove /data/local/tmp/bliss-native-auth.json before reusing the emulator.' }
        Remove-Item -LiteralPath $inputPath -ErrorAction SilentlyContinue
    }
} finally { Pop-Location }
