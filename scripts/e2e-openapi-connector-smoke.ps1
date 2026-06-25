param(
    [string]$BaseUrl = "http://127.0.0.1",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$PostgresContainer = "seahorse-postgres",
    [string]$PostgresUser = "seahorse",
    [string]$PostgresDatabase = "seahorse"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$NodeScript = Join-Path $ScriptDir "e2e-openapi-connector-smoke.mjs"

node $NodeScript `
  --base-url $BaseUrl `
  --username $Username `
  --password $Password `
  --postgres-container $PostgresContainer `
  --postgres-user $PostgresUser `
  --postgres-database $PostgresDatabase
