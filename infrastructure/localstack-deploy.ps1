$ErrorActionPreference = 'Stop'

function Invoke-Checked([string]$Command, [string[]]$Arguments) {
  & $Command @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code ${LASTEXITCODE}: $Command $($Arguments -join ' ')"
  }
}

$script:awsCommand = if (Get-Command 'awslocal' -ErrorAction SilentlyContinue) {
  'awslocal'
} else {
  'lstk'
}
$script:awsArgumentsPrefix = if ($script:awsCommand -eq 'lstk') {
  @('aws')
} else {
  @()
}

function Invoke-AwsChecked([string[]]$Arguments) {
  $fullArguments = @($script:awsArgumentsPrefix) + @($Arguments)
  & $script:awsCommand @fullArguments
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code ${LASTEXITCODE}: $($script:awsCommand) $($fullArguments -join ' ')"
  }
}

function Show-StackEvents([string]$StackName) {
  Write-Host "`nCloudFormation events for failed stack '$StackName':"
  $eventArguments = @($script:awsArgumentsPrefix) + @(
    'cloudformation', 'describe-stack-events', '--stack-name', $StackName
  )
  $rawEvents = (& $script:awsCommand @eventArguments |
    Out-String).Trim()
  if ($LASTEXITCODE -ne 0) {
    Write-Warning "Unable to retrieve CloudFormation events for stack '$StackName'."
    Write-Host $rawEvents
    return
  }

  try {
    $events = $rawEvents | ConvertFrom-Json
    $failedEvents = @(
      $events.StackEvents |
        Where-Object { $_.ResourceStatus -like '*_FAILED' } |
        Select-Object Timestamp, LogicalResourceId, ResourceType, ResourceStatus,
          ResourceStatusReason
    )

    if ($failedEvents.Count -gt 0) {
      $failedEvents | Format-Table -AutoSize | Out-String | Write-Host
    } else {
      Write-Host $rawEvents
    }
  } catch {
    Write-Warning 'Unable to parse CloudFormation events; printing the raw response.'
    Write-Host $rawEvents
  }
}

$templateDirectory = Join-Path $PSScriptRoot 'cdk.out'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$imageNames = @(
  'patient-service',
  'billing-service',
  'analytics-service',
  'auth-service',
  'api-gateway'
)

$localstackEndpoint = $env:AWS_ENDPOINT_URL
if ([string]::IsNullOrWhiteSpace($localstackEndpoint)) {
  $localstackEndpoint = 'http://localhost:4566'
}
$localstackEndpoint = $localstackEndpoint.TrimEnd('/')

$health = Invoke-RestMethod -Uri "$localstackEndpoint/_localstack/health"
if (-not $health) {
  throw 'LocalStack health endpoint returned no response.'
}

if ([string]::IsNullOrWhiteSpace($env:IMAGE_PREFIX)) {
  foreach ($imageName in $imageNames) {
    & docker image inspect "$($imageName):latest" *> $null
    if ($LASTEXITCODE -ne 0) {
      throw "Required image is missing: $imageName`:latest. Run scripts/build-images.ps1 first."
    }
  }
} else {
  Write-Host "Using registry image prefix: $env:IMAGE_PREFIX"
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

$templateCandidates = @(
  Get-ChildItem -LiteralPath $templateDirectory -Filter '*.template.json' -File -ErrorAction SilentlyContinue
)

if ($templateCandidates.Count -eq 0) {
  throw "No synthesized CloudFormation template was found in: $templateDirectory"
}

if ($templateCandidates.Count -gt 1) {
  $template = $templateCandidates |
    Where-Object { $_.Name -eq 'localstack.template.json' } |
    Select-Object -First 1
  if ($null -eq $template) {
    throw "Multiple synthesized templates were found; unable to choose one: $($templateCandidates.Name -join ', ')"
  }
} else {
  $template = $templateCandidates[0]
}

$template = $template.FullName
Write-Host "Using synthesized template: $template"

try {
  Invoke-AwsChecked @('cloudformation', 'deploy', '--stack-name', 'patient-management',
    '--template-file', $template, '--capabilities', 'CAPABILITY_IAM')
} catch {
  Show-StackEvents 'patient-management'
  throw
}
Invoke-AwsChecked @('cloudformation', 'describe-stacks', '--stack-name', 'patient-management')
Invoke-AwsChecked @('ecs', 'list-clusters')
Invoke-AwsChecked @('rds', 'describe-db-instances')
Invoke-AwsChecked @('elbv2', 'describe-load-balancers')
