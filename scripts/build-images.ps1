$ErrorActionPreference = 'Stop'

$modules = @(
  'patient-service',
  'billing-service',
  'analytics-service',
  'auth-service',
  'api-gateway'
)

foreach ($module in $modules) {
  $modulePath = Join-Path $PSScriptRoot "..\$module"
  $dockerfile = Join-Path $modulePath 'Dockerfile'
  if (-not (Test-Path -LiteralPath $dockerfile)) {
    throw "Missing Dockerfile for ${module}: $dockerfile"
  }

  Write-Host "Building $module..."
  & docker build --tag "$($module):latest" --tag "$($module):0.1.0" $modulePath
  if ($LASTEXITCODE -ne 0) {
    throw "Docker build failed for $module."
  }
}

Write-Host "`nImages required by the CDK stack:"
foreach ($module in $modules) {
  & docker image inspect "$($module):latest" --format '{{.RepoTags}}'
}
