$ErrorActionPreference = 'Stop'

function Invoke-Checked([string]$Command, [string[]]$Arguments) {
  & $Command @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code ${LASTEXITCODE}: $Command $($Arguments -join ' ')"
  }
}

$template = Join-Path $PSScriptRoot 'cdk.out/localstack.template.json'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$imageNames = @(
  'patient-service',
  'billing-service',
  'analytics-service',
  'auth-service',
  'api-gateway'
)

$health = Invoke-RestMethod -Uri 'http://localhost:4566/_localstack/health'
if (-not $health) {
  throw 'LocalStack health endpoint returned no response.'
}

foreach ($imageName in $imageNames) {
  & docker image inspect "$($imageName):latest" *> $null
  if ($LASTEXITCODE -ne 0) {
    throw "Required image is missing: $imageName`:latest. Run scripts/build-images.ps1 first."
  }
}

$hostMaven = Get-Command 'mvn' -ErrorAction SilentlyContinue
if ($null -ne $hostMaven) {
  Push-Location $PSScriptRoot
  try {
    Invoke-Checked 'mvn' @('-B', 'compile', 'exec:java')
  } finally {
    Pop-Location
  }
} else {
  Write-Host 'Maven was not found on the host; using the Java 21 Maven container.'
  Invoke-Checked 'docker' @('run', '--rm', '--volume', "$projectRoot`:/workspace",
    '--workdir', '/workspace/infrastructure', 'maven:3.9.9-eclipse-temurin-21',
    'mvn', '-B', 'compile', 'exec:java')
}

if (-not (Test-Path -LiteralPath $template)) {
  throw "Synthesized template was not found: $template"
}

Invoke-Checked 'lstk' @('aws', 'cloudformation', 'deploy', '--stack-name', 'patient-management',
  '--template-file', $template, '--capabilities', 'CAPABILITY_IAM')
Invoke-Checked 'lstk' @('aws', 'cloudformation', 'describe-stacks', '--stack-name', 'patient-management')
Invoke-Checked 'lstk' @('aws', 'ecs', 'list-clusters')
Invoke-Checked 'lstk' @('aws', 'rds', 'describe-db-instances')
Invoke-Checked 'lstk' @('aws', 'elbv2', 'describe-load-balancers')
