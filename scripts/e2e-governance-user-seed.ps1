$ErrorActionPreference = "Stop"

function ConvertTo-SeahorseSqlLiteral {
    param([AllowNull()][string]$Value)
    if ($null -eq $Value) {
        return "NULL"
    }
    return "'" + ($Value -replace "'", "''") + "'"
}

function Invoke-SeahorsePostgresSql {
    param(
        [string]$PostgresContainer,
        [string]$PostgresUsername,
        [string]$PostgresDatabase,
        [string]$Sql
    )

    $output = & docker.exe exec $PostgresContainer psql `
        -U $PostgresUsername `
        -d $PostgresDatabase `
        -v "ON_ERROR_STOP=1" `
        -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed in container $PostgresContainer with exit code $LASTEXITCODE`n$output"
    }
    return $output
}

function Ensure-SeahorseGovernanceNormalUser {
    param(
        [string]$Username,
        [string]$Password,
        [string]$TenantId = "default",
        [string]$PostgresContainer = "seahorse-postgres",
        [string]$PostgresUsername = "seahorse",
        [string]$PostgresDatabase = "seahorse"
    )

    $usernameLiteral = ConvertTo-SeahorseSqlLiteral $Username
    $passwordLiteral = ConvertTo-SeahorseSqlLiteral $Password
    $tenantLiteral = ConvertTo-SeahorseSqlLiteral $TenantId

    $sql = @"
SELECT set_config('app.current_tenant_id', $tenantLiteral, false);
WITH candidate_user AS (
    SELECT COALESCE(
        (SELECT id FROM t_user WHERE username = $usernameLiteral),
        (SELECT GREATEST(COALESCE(MAX(id), 2001523723396308993) + 1, 2001523723396308994) FROM t_user)
    ) AS id
)
INSERT INTO t_user (id, username, password, role, avatar, tenant_id, email, status, create_time, update_time, deleted)
SELECT id, $usernameLiteral, $passwordLiteral, 'user', NULL, $tenantLiteral, NULL, 'ACTIVE',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM candidate_user
ON CONFLICT (username) DO UPDATE
SET password = EXCLUDED.password,
    role = EXCLUDED.role,
    tenant_id = EXCLUDED.tenant_id,
    status = 'ACTIVE',
    deleted = 0,
    update_time = CURRENT_TIMESTAMP;
"@

    Invoke-SeahorsePostgresSql `
        -PostgresContainer $PostgresContainer `
        -PostgresUsername $PostgresUsername `
        -PostgresDatabase $PostgresDatabase `
        -Sql $sql | Out-Null
}
