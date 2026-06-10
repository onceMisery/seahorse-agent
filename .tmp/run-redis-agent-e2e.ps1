$ErrorActionPreference = 'Stop'

$base = 'http://127.0.0.1:9090'
$loginBody = @{ username = 'admin'; password = 'admin123' } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $loginBody -TimeoutSec 60
$token = $login.data.token
if (-not $token) {
    throw 'Login did not return token'
}

$conversationId = 'codex-redis-e2e-' + (Get-Date -Format 'yyyyMMddHHmmss')
$questionBase64 = '6K+35Z+65LqOIFJlZGlzIOWumOaWuSBHaXRIdWIg5LuT5bqTIGh0dHBzOi8vZ2l0aHViLmNvbS9yZWRpcy9yZWRpcyDlgZrkuIDmrKHlrozmlbTnmoTlm77mlofpobnnm67or6bnu4bku4vnu43jgILnoazmgKfopoHmsYLvvJoKMS4g5b+F6aG76K+75Y+W5LuT5bqTIFJFQURNReOAgWRvY3Mg5ZKM5qC45b+D5rqQ56CB5paH5Lu244CCCjIuIOW/hemhu+iHs+WwkeaIkOWKn+iwg+eUqOS4gOasoSBnaXRodWJfcmVwb3NpdG9yeV9yZWFkZXLjgIF3ZWJfZmV0Y2jjgIFpbWFnZV9nZW5lcmF0aW9u44CBbmV3c2xldHRlcl9nZW5lcmF0aW9u44CBcHB0X2dlbmVyYXRpb27jgIFjaGFydF92aXN1YWxpemF0aW9u44CBZnJvbnRlbmRfZGVzaWduIOS4g+S4quW3peWFt+OAggozLiDmnIDnu4jovpPlh7rkuK3mlocgTWFya2Rvd27vvIzlv4XpobvljIXlkKvpobnnm67mpoLop4jjgIHmnrbmnoTorr7orqHjgIHmnrbmnoTlm77jgIHmtYHnqIvlm77jgIHmoLjlv4PpgLvovpHjgIHph43ngrnnibnmgKfjgIHlhbPplK7mlofku7bor4Hmja7ooajjgIHnlJ/miJDlm77niYflvJXnlKjjgIIKNC4gTWVybWFpZCDlv4Xpobvkvb/nlKjni6znq4vku6PnoIHlnZfvvIznrKzkuIDooYzlj6rog73mmK8gYGBgbWVybWFpZO+8jOesrOS6jOihjOW8gOWniyBncmFwaCDmiJYgZmxvd2NoYXJ077yM5LiN6KaB6L6T5Ye6IGBgYG1lcm1haWRncmFwaOOAggo1LiDlkIzkuIDkuKogTWVybWFpZCDlm77lhoXoioLngrkgSUQg5b+F6aG75ZSv5LiA44CCCjYuIOS4jeimgeaKiiBNYXJrZG93biDljovnvKnmiJDljZXooYzjgII='
$question = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($questionBase64))

$query = [ordered]@{
    question = $question
    conversationId = $conversationId
    userId = '2001523723396308993'
    chatMode = 'agent'
    agentId = 'github-visual-project-intro-agent'
    versionId = 'github-visual-project-intro-agent-v1'
    deepThinking = 'false'
}
$qs = ($query.GetEnumerator() | ForEach-Object {
    [uri]::EscapeDataString($_.Key) + '=' + [uri]::EscapeDataString([string]$_.Value)
}) -join '&'

$outDir = Join-Path $PWD '.tmp'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$outFile = Join-Path $outDir ("redis-agent-e2e-" + (Get-Date -Format 'yyyyMMddHHmmss') + ".sse.txt")
$headers = @{ Authorization = $token; 'X-User-Id' = '2001523723396308993' }

Invoke-WebRequest -UseBasicParsing -Method Get -Uri "$base/rag/v3/chat?$qs" -Headers $headers -TimeoutSec 1200 -OutFile $outFile

Write-Output "conversationId=$conversationId"
Write-Output "outFile=$outFile"
