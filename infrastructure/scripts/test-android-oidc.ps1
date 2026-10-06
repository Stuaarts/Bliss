#Requires -Version 7.0
param(
    [ValidateSet('authentication', 'marketplace')]
    [string]$Scenario = 'authentication',
    [ValidateSet('all', 'request', 'quote', 'accept', 'fulfil', 'review')]
    [string]$MarketplacePhase = 'all',
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
$results = Join-Path $root $(if ($Scenario -eq 'marketplace') { ".work/native-marketplace/$RunId-$MarketplacePhase" } else { '.work/native-oidc' })
$inputPath = Join-Path $results 'accounts.json'
$deviceInput = '/data/local/tmp/bliss-native-auth.json'
if ($Scenario -eq 'authentication' -and $MarketplacePhase -ne 'all') {
    throw 'MarketplacePhase applies only to the marketplace scenario.'
}

function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed: $Program" }
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
    $accounts = Get-Content '.work/demo-accounts.json' -Raw | ConvertFrom-Json
    # Profiles can be renamed in the demo. Read only the two expected display names.
    $sql = "SELECT coalesce(json_object_agg(oidc_subject, display_name), '{}'::json) FROM bliss_user WHERE oidc_subject IN ('50000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000002');"
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
    foreach ($name in @('customer', 'provider')) {
        $account = @($accounts | Where-Object username -eq $name)
        if ($account.Count -ne 1 -or -not $account[0].password) { throw "Missing generated demo identity: $name" }
        $subject = if ($name -eq 'customer') { '50000000-0000-0000-0000-000000000001' } else { '50000000-0000-0000-0000-000000000002' }
        if (-not $profiles[$subject]) { throw 'Initialize both local demo profiles before running this check.' }
        $fixture[$name] = @{ username = $name; password = $account[0].password; displayName = $profiles[$subject] }
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
    try {
        $fixture | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $inputPath -Encoding utf8NoBOM
        Invoke-Checked $adb @('-s', $DeviceId, 'push', $inputPath, $deviceInput)
        Invoke-Checked $adb @('-s', $DeviceId, 'shell', 'chmod', '600', $deviceInput)
        $logPath = Join-Path $results 'instrumentation.log'
        $instrumentation = @('-s', $DeviceId, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'blissRun')
        if ($Scenario -eq 'marketplace') {
            $instrumentation += @('marketplace', '-e', 'runId', $RunId, '-e', 'marketplacePhase', $MarketplacePhase,
                '-e', 'class', 'com.bliss.native_smoke.NativeMarketplaceTest')
        } else {
            $instrumentation += @('local-demo', '-e', 'class', 'com.bliss.native_smoke.NativeOidcTest')
        }
        $instrumentation += 'com.bliss.native_smoke.test/androidx.test.runner.AndroidJUnitRunner'
        $process = Start-Process -FilePath $adb -WindowStyle Hidden -PassThru -ArgumentList $instrumentation `
            -RedirectStandardOutput $logPath -RedirectStandardError (Join-Path $results 'instrumentation-errors.log')
        $timeout = if ($Scenario -eq 'marketplace') { 600000 } else { 240000 }
        if (-not $process.WaitForExit($timeout)) {
            $process.Kill()
            & $adb -s $DeviceId shell am force-stop com.bliss.native_smoke
            throw 'Native check exceeded its time limit; inspect the ignored logs.'
        }
        $log = Get-Content -LiteralPath $logPath -Raw
        # Android instrumentation may return exit 0 even when an assertion fails.
        $checkpoint = if ($Scenario -eq 'authentication') { 'Switch back to customer after identity-provider logout' }
            elseif ($MarketplacePhase -eq 'all') { 'Native marketplace lifecycle finished; signed out' } else { 'Verified: Native' }
        if ($process.ExitCode -ne 0 -or $log -notmatch 'OK \(1 test\)' -or $log -notmatch $checkpoint) {
            throw "Native check failed. Inspect $logPath (kept local)."
        }
        $screenshots = if ($Scenario -eq 'authentication') {
            @('android-customer-restored.png', 'android-provider-signed-in.png', 'android-signed-out.png')
        } elseif ($MarketplacePhase -eq 'all') {
            @('android-marketplace-request.png', 'android-marketplace-chat.png', 'android-marketplace-provider-complete.png',
                'android-marketplace-completed.png', 'android-marketplace-review.png')
        } else { @() }
        foreach ($name in $screenshots) {
            Invoke-Checked $adb @('-s', $DeviceId, 'pull', "/sdcard/Android/data/com.bliss.native_smoke/files/$name", (Join-Path $results $name))
        }
        Write-Output "PASS: $Scenario ($MarketplacePhase). See instrumentation checkpoints for verified actions."
        Write-Output "Logs and credential-free app screenshots: $results"
    } finally {
        & $adb -s $DeviceId shell rm -f $deviceInput
        if ($LASTEXITCODE -ne 0) { Write-Warning 'Device input cleanup failed; remove /data/local/tmp/bliss-native-auth.json before reusing the emulator.' }
        Remove-Item -LiteralPath $inputPath -ErrorAction SilentlyContinue
    }
} finally { Pop-Location }
