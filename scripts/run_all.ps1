$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "..")
$composeFile = Join-Path $root "collector/docker-compose.yaml"
$javaJar = Join-Path $root "java/samples/rag-springboot/target/rag-springboot-0.1.0-SNAPSHOT.jar"
$javaProc = $null
$dotnetProc = $null

if (Get-Command docker -ErrorAction SilentlyContinue) {
    try {
        docker compose version | Out-Null
        $composeCmd = @("docker", "compose")
    } catch {
        # ignore, fallback check below
    }
}
if (-not $composeCmd -and (Get-Command docker-compose -ErrorAction SilentlyContinue)) {
    $composeCmd = @("docker-compose")
}
if (-not $composeCmd) {
    throw "Docker Compose is required. Install Docker Desktop or docker-compose."
}

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $Args)
    if ($composeCmd.Count -gt 1) {
        & $composeCmd[0] @($composeCmd[1..($composeCmd.Count - 1)]) @Args
    } else {
        & $composeCmd[0] @Args
    }
}

function Cleanup {
    if ($javaProc -and !$javaProc.HasExited) {
        try { $javaProc.Kill() } catch {}
    }
    if ($dotnetProc -and !$dotnetProc.HasExited) {
        try { $dotnetProc.Kill() } catch {}
    }
    try { Invoke-Compose -Args "-f", $composeFile, "down", "--remove-orphans" | Out-Null } catch {}
}

trap {
    Cleanup
    throw
}

Push-Location $root
try {
    ./mvnw -pl libs/langchain4j-otel -am package
    ./mvnw -pl samples/rag-springboot -am package

    dotnet build (Join-Path $root "dotnet/libs/sk-otel/SkOtel.csproj")
    dotnet build (Join-Path $root "dotnet/samples/sk-chat/SkChat.csproj")

    Invoke-Compose -Args "-f", $composeFile, "up", "-d", "--remove-orphans" | Out-Null
    Start-Sleep -Seconds 5

    if (-not (Test-Path $javaJar)) {
        throw "Unable to locate Spring Boot executable JAR at $javaJar"
    }

    $javaProc = Start-Process -FilePath "java" -ArgumentList "-jar", $javaJar -PassThru
    $dotnetProc = Start-Process -FilePath "dotnet" -ArgumentList "run", "--project", (Join-Path $root "dotnet/samples/sk-chat/SkChat.csproj"), "--urls", "http://localhost:7080" -PassThru

    $grafanaUrl = "http://localhost:3000/d/genai-overview"
    try { Start-Process $grafanaUrl | Out-Null } catch {}

    Write-Host "`n🚀 OpenTelemetry GenAI Bridges stack is live"
    Write-Host "  • Spring Boot RAG service: http://localhost:8080"
    Write-Host "  • Semantic Kernel chat:   http://localhost:7080"
    Write-Host "  • Grafana dashboards:     $grafanaUrl"
    Write-Host "Press Ctrl+C to stop everything."

    Wait-Process -Id @($javaProc.Id, $dotnetProc.Id)
}
finally {
    Cleanup
    Pop-Location
}
