param(
    [switch]$IncludeLive,
    [switch]$IncludeTenantSigned,
    [string]$MainUrl = "http://127.0.0.1:9090/a2a",
    [int]$RemotePort = 9092,
    [string]$NacosServer = "127.0.0.1:8848",
    [string]$TenantId = "tenant-a",
    [string]$SharedSecret = $env:SEAHORSE_A2A_SHARED_SECRET,
    [string]$MainAgentName = "seahorse-a"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($SharedSecret)) {
    $SharedSecret = "seahorse-local-a2a-token"
}

$agentScopeUnitTests = @(
    "AgentScopeReActExecutorTests",
    "A2aAgentRemoteInvokerTests",
    "AgentScopeA2aServerControllerTests",
    "AgentScopeRunMetadataContributorTests",
    "AgentScopeReActAutoConfigurationTests",
    "AgentScopePromptConfigCenterTests",
    "AgentScopeConfigCenterStartupValidatorTests",
    "AgentScopePropertiesTests",
    "AgentScopeA2AAgentConnectorTests",
    "AgentScopeAgentCardFactoryTests",
    "AgentScopeAgentCardRegistrarTests",
    "AgentScopeA2aE2eScriptContractTests",
    "AgentScopeReleaseGateScriptContractTests",
    "NacosPropertiesFactoryTests",
    "AgentScopeA2AToolPortAdapterTests",
    "A2ATenantMetadataTests",
    "AgentScopeModelBridgeTests",
    "AgentScopeToolFactoryTests",
    "ReActAgentScopeAgentClientTests"
) -join ","

function Invoke-GateStep {
    param(
        [string]$Name,
        [scriptblock]$Command
    )

    Write-Host "AGENTSCOPE_GATE_STEP=$Name"
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "AGENTSCOPE_GATE_STEP=$Name failed with exit $LASTEXITCODE"
    }
}

Push-Location $repoRoot
try {
    # mvn -pl seahorse-agent-adapter-agent-agentscope -am test
    Invoke-GateStep "agentscope-unit" {
        & mvn -pl seahorse-agent-adapter-agent-agentscope -am `
            "-Dtest=$agentScopeUnitTests" `
            "-DfailIfNoTests=false" `
            "-Dsurefire.failIfNoSpecifiedTests=false" `
            "-DforkCount=0" `
            test
    }

    Invoke-GateStep "agentscope-kernel-run-contracts" {
        & mvn -pl seahorse-agent-kernel `
            "-Dtest=KernelChatAgentRunStoreTests" `
            "-DforkCount=0" `
            test
    }

    Invoke-GateStep "agentscope-application-smoke" {
        & mvn -pl seahorse-agent-tests -am `
            "-Dtest=KernelChatInboundServiceAgentScopeEngineSmokeTests" `
            "-DfailIfNoTests=false" `
            "-Dsurefire.failIfNoSpecifiedTests=false" `
            "-DforkCount=0" `
            test
    }

    # mvn -pl seahorse-agent-bootstrap -am package
    Invoke-GateStep "bootstrap-package" {
        & mvn -pl seahorse-agent-bootstrap -am package `
            -DskipTests `
            "-Dmaven.test.skip=true" `
            "-Dspotless.check.skip=true"
    }

    if ($IncludeLive) {
        Invoke-GateStep "a2a-live-shared-secret" {
            & .\scripts\agentscope-a2a-e2e.ps1 `
                -MainUrl $MainUrl `
                -RemotePort $RemotePort `
                -NacosServer $NacosServer `
                -TenantId $TenantId `
                -SharedSecret $SharedSecret `
                -AuthMode shared-secret `
                -MainAgentName $MainAgentName
        }
    }

    if ($IncludeTenantSigned) {
        Invoke-GateStep "a2a-live-tenant-signed" {
            & .\scripts\agentscope-a2a-e2e.ps1 `
                -MainUrl $MainUrl `
                -RemotePort $RemotePort `
                -NacosServer $NacosServer `
                -TenantId $TenantId `
                -SharedSecret $SharedSecret `
                -AuthMode tenant-signed `
                -MainAgentName $MainAgentName
        }
    }

    Write-Host "AGENTSCOPE_RELEASE_GATE=PASS"
} finally {
    Pop-Location
}
