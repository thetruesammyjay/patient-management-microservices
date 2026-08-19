$ErrorActionPreference = 'Stop'

function Invoke-Checked([string]$Command, [string[]]$Arguments) {
  & $Command @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code ${LASTEXITCODE}: $Command $($Arguments -join ' ')"
  }
}

$template = Join-Path $PSScriptRoot 'cdk.out/localstack.template.json'

$health = Invoke-RestMethod -Uri 'http://localhost:4566/_localstack/health'
if (-not $health) {
  throw 'LocalStack health endpoint returned no response.'
}

Push-Location $PSScriptRoot
try {
  Invoke-Checked 'mvn' @('-B', 'compile', 'exec:java')
} finally {
  Pop-Location
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
