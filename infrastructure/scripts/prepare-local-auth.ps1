$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$destination = Join-Path $root '.work/keycloak'
$credentialsPath = Join-Path $root '.work/demo-accounts.json'
New-Item -ItemType Directory -Path $destination -Force | Out-Null
if (Test-Path -LiteralPath $credentialsPath) {
    $accounts = Get-Content -LiteralPath $credentialsPath -Raw | ConvertFrom-Json
} else {
    $accounts = @('customer','provider','outsider','admin') | ForEach-Object {
        @{ username = $_; password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(18)) }
    }
    $accounts | ConvertTo-Json | Set-Content -LiteralPath $credentialsPath
}
$realm = Get-Content (Join-Path $root 'infrastructure/local/keycloak/bliss-realm.template.json') -Raw | ConvertFrom-Json
$users = foreach ($account in $accounts) {
    $roles = switch ($account.username) {
        'provider' { @('customer','provider') }
        'outsider' { @('customer','provider') }
        'admin' { @('customer','admin') }
        default { @('customer') }
    }
    $id = switch ($account.username) {
        'provider' { '50000000-0000-0000-0000-000000000002' }
        'outsider' { '50000000-0000-0000-0000-000000000003' }
        'admin' { '50000000-0000-0000-0000-000000000004' }
        default { '50000000-0000-0000-0000-000000000001' }
    }
    @{ id=$id; username=$account.username; enabled=$true; emailVerified=$true;
       firstName=$account.username; lastName='Demo'; email="$($account.username)@example.test";
       realmRoles=@($roles); credentials=@(@{type='password'; value=$account.password; temporary=$false}) }
}
$realm | Add-Member -NotePropertyName users -NotePropertyValue @($users) -Force
$realm | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $destination 'bliss-realm.json')
Write-Output 'Prepared ignored local Keycloak realm. Demo credentials are in .work/demo-accounts.json.'
Write-Output 'Realm import only applies when creating the realm; it never silently replaces existing users.'
