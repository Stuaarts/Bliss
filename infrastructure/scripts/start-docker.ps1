param([int]$TimeoutSeconds = 60)
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'This helper is for Windows Docker Desktop only.' }
if ((docker context show) -ne 'desktop-linux') { throw 'Select the Docker Desktop Linux context before using this helper.' }
function Test-DockerEngine {
    $probe = [Diagnostics.Process]::new()
    $probe.StartInfo = [Diagnostics.ProcessStartInfo]::new('docker', 'info --format {{.ServerVersion}}')
    $probe.StartInfo.UseShellExecute = $false
    $probe.StartInfo.CreateNoWindow = $true
    $probe.StartInfo.RedirectStandardOutput = $true
    $probe.StartInfo.RedirectStandardError = $true
    try {
        [void]$probe.Start()
        if (-not $probe.WaitForExit(3000)) { $probe.Kill(); return $false }
        return $probe.ExitCode -eq 0
    } finally { $probe.Dispose() }
}
if (Test-DockerEngine) { Write-Output 'Docker engine is ready.'; return }

# Never touch runtime files while Desktop is running, and never touch its disk/volumes.
$desktopRunning = Get-Process -Name 'Docker Desktop','com.docker.backend' -ErrorAction SilentlyContinue
if (-not $desktopRunning) {
    $dockerRoot = [IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'Docker'))
    $runtime = [IO.Path]::GetFullPath((Join-Path $dockerRoot 'run'))
    $backup = [IO.Path]::GetFullPath((Join-Path $dockerRoot ('run.before-bliss-start-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))))
    if (-not $runtime.StartsWith($dockerRoot + '\') -or -not $backup.StartsWith($dockerRoot + '\')) {
        throw 'Unsafe Docker runtime path.'
    }
    if (Test-Path -LiteralPath $runtime) {
        [IO.Directory]::Move($runtime, $backup)
        Write-Output "Preserved stale Docker runtime at $backup"
    }
    New-Item -ItemType Directory -Path $runtime -Force | Out-Null
    # Recent Desktop versions also create a socket-only secrets-engine directory.
    # Refuse to rotate it if it ever contains persistent data or unexpected files.
    $localRoot = [IO.Path]::GetFullPath($env:LOCALAPPDATA)
    $secretsRuntime = [IO.Path]::GetFullPath((Join-Path $localRoot 'docker-secrets-engine'))
    $secretsBackup = [IO.Path]::GetFullPath($secretsRuntime + '.before-bliss-start-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    if (-not $secretsRuntime.StartsWith($localRoot + '\') -or -not $secretsBackup.StartsWith($localRoot + '\')) { throw 'Unsafe secrets runtime path.' }
    if (Test-Path -LiteralPath $secretsRuntime) {
        $unexpected = Get-ChildItem -LiteralPath $secretsRuntime -Force | Where-Object { $_.Name -notin @('engine.sock','engine.sock.stale') }
        if ($unexpected) { throw 'Secrets engine contains non-runtime files; manual inspection required.' }
        # Rename the directory atomically. Move-Item can leave socket reparse
        # points behind while moving the other children on Windows.
        [IO.Directory]::Move($secretsRuntime, $secretsBackup)
        New-Item -ItemType Directory -Path $secretsRuntime | Out-Null
        Write-Output "Preserved secrets runtime at $secretsBackup"
    }
    $desktop = Join-Path $env:ProgramFiles 'Docker/Docker/Docker Desktop.exe'
    if (-not (Test-Path -LiteralPath $desktop)) { throw 'Docker Desktop is not installed at the standard location.' }
    Start-Process -FilePath $desktop -WindowStyle Hidden
}
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    Start-Sleep -Seconds 2
    if (Test-DockerEngine) { Write-Output 'Docker engine is ready.'; return }
} while ((Get-Date) -lt $deadline)
throw 'Docker did not start. Inspect Docker host logs; if Desktop displays a crash, quit it and rerun this helper. No data was reset.'
