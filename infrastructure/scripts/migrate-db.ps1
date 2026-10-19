[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('validate', 'migrate')][string]$Action,
    [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$Database,
    [string]$ApplicationDirectory = (Join-Path $PSScriptRoot '../../services/api/target/quarkus-app')
)

$ErrorActionPreference = 'Stop'
$application = (Resolve-Path -LiteralPath $ApplicationDirectory).Path
foreach ($directory in @('app', 'lib/main', 'lib/boot')) {
    if (-not (Test-Path -LiteralPath (Join-Path $application $directory) -PathType Container)) {
        throw 'A complete Quarkus fast-jar application directory is required.'
    }
}
$java = 'java'
if ($env:JAVA_HOME) {
    $java = Join-Path $env:JAVA_HOME 'bin/java'
}
$classpath = (@('app/*', 'lib/main/*', 'lib/boot/*') | ForEach-Object {
    Join-Path $application $_
}) -join [IO.Path]::PathSeparator

# Dedicated migration variables are inherited through the environment. No secret arguments,
# Quarkus bootstrap, local profile, seed override, clean or repair operation is provided.
& $java '-cp' $classpath 'com.bliss.operations.DatabaseMigration' $Action.ToLowerInvariant() '--database' $Database
exit $LASTEXITCODE
