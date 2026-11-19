$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

function Invoke-Sql($connection, [string]$sql, $transaction = $null) {
    $command = $connection.CreateCommand()
    try {
        $command.Transaction = $transaction
        $command.CommandText = $sql
        [void]$command.ExecuteNonQuery()
    } finally {
        $command.Dispose()
    }
}

function Invoke-SqlFile($connection, [string]$file, $transaction) {
    $content = Get-Content -LiteralPath $file -Raw -Encoding UTF8
    foreach ($batch in ($content -split '(?im)^\s*GO[ \t]*\r?$')) {
        if ($batch.Trim()) { Invoke-Sql $connection $batch $transaction }
    }
}

$app = $null
try {
    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    if (-not $java) { throw 'Instale o JDK 21 e adicione java ao PATH.' }
    $javaVersion = & $java.Source --version | Select-Object -First 1
    if ($javaVersion -notmatch '\b21(?:\.|\s|$)') {
        throw 'Configure o PATH para usar o JDK 21.'
    }

    $sqlPort = 1433
    if ($env:BRASA_SQL_PORTA) {
        if (-not [int]::TryParse($env:BRASA_SQL_PORTA, [ref]$sqlPort) -or
            $sqlPort -lt 1 -or $sqlPort -gt 65535) {
            throw 'BRASA_SQL_PORTA deve ser uma porta entre 1 e 65535.'
        }
    }

    $url = 'http://127.0.0.1:5000/'
    if (Get-NetTCPConnection -LocalPort 5000 -State Listen -ErrorAction SilentlyContinue) {
        throw 'A porta 5000 ja esta em uso. Feche o sistema ou o programa que esta usando essa porta.'
    }

    Write-Host 'Conectando ao SQL Server e preparando o banco...'
    $connection = New-Object System.Data.SqlClient.SqlConnection (
        "Server=tcp:localhost,$sqlPort;Database=master;Integrated Security=True;Encrypt=True;TrustServerCertificate=True;Connection Timeout=5"
    )
    try {
        try { $connection.Open() } catch {
            throw "Nao foi possivel conectar ao SQL Server na porta $sqlPort com a conta do Windows. Confira se o servico esta iniciado, se o TCP/IP esta habilitado e se sua conta tem permissao. $($_.Exception.Message)"
        }
        Invoke-Sql $connection @'
IF CONVERT(int, SERVERPROPERTY('IsIntegratedSecurityOnly')) = 1
    THROW 50000, 'Habilite a autenticacao mista no SQL Server e reinicie o servico.', 1;
IF DB_ID(N'Totem_Atendimento') IS NULL CREATE DATABASE Totem_Atendimento;
'@
        $connection.ChangeDatabase('Totem_Atendimento')
        $directory = Join-Path $env:LOCALAPPDATA 'BrasaDeRua'
        [void](New-Item -ItemType Directory -Path $directory -Force)
        $credentialFile = Join-Path $directory "sql-$sqlPort.clixml"
        if (-not (Test-Path -LiteralPath $credentialFile)) {
            $login = 'brasa_local_' + [guid]::NewGuid().ToString('N')
            $password = 'Aa1!' + [guid]::NewGuid().ToString('N')
            $credential = New-Object System.Management.Automation.PSCredential (
                $login, (ConvertTo-SecureString $password -AsPlainText -Force)
            )
            $credential | Export-Clixml -LiteralPath $credentialFile
        }
        $credential = Import-Clixml -LiteralPath $credentialFile
        $login = $credential.UserName
        $password = $credential.GetNetworkCredential().Password
        if ($login -cnotmatch '^brasa_local_[a-f0-9]{32}$' -or
            $password -cnotmatch '^Aa1![a-f0-9]{32}$') {
            throw 'O arquivo de credenciais locais esta invalido.'
        }
        $transaction = $connection.BeginTransaction()
        try {
            Invoke-SqlFile $connection 'banco/01-estrutura.sql' $transaction
            Invoke-SqlFile $connection 'banco/02-cardapio.sql' $transaction
            Invoke-Sql $connection @"
IF SUSER_ID('$login') IS NULL CREATE LOGIN [$login] WITH PASSWORD = '$password';
IF USER_ID('$login') IS NULL CREATE USER [$login] FOR LOGIN [$login];
GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::dbo TO [$login];
GRANT EXECUTE ON OBJECT::dbo.FinalizarPedido TO [$login];
"@ $transaction
            $transaction.Commit()
        } finally {
            $transaction.Dispose()
        }
    } finally {
        $connection.Dispose()
    }
    $env:TOTEM_SQL_URL = "jdbc:sqlserver://localhost:${sqlPort};databaseName=Totem_Atendimento;encrypt=true;trustServerCertificate=true"
    $env:TOTEM_SQL_USUARIO = $login
    $env:TOTEM_SQL_SENHA = $password
    $env:TOTEM_HOST = '127.0.0.1'
    $env:TOTEM_PORTA = '5000'
    $env:TOTEM_HTTPS = 'false'
    while ([string]::IsNullOrWhiteSpace($env:TOTEM_ADMIN_SENHA)) {
        $secret = Read-Host 'Escolha a senha da area da equipe para esta sessao' -AsSecureString
        $env:TOTEM_ADMIN_SENHA = [System.Net.NetworkCredential]::new('', $secret).Password
    }

    Write-Host 'Compilando o projeto...'
    & .\mvnw.cmd -B package
    if ($LASTEXITCODE -ne 0) { throw 'O Maven nao conseguiu compilar o projeto.' }

    $app = Start-Process -FilePath $java.Source -ArgumentList '-jar', 'target/brasa-de-rua-1.0.0.jar' -NoNewWindow -PassThru
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline) {
        if ($app.HasExited) { throw 'O sistema encerrou durante a inicializacao. Confira a mensagem acima.' }
        try {
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 6
            if ($response.StatusCode -eq 200) { break }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    if (-not $response -or $response.StatusCode -ne 200) {
        throw 'O cardapio nao respondeu. Confira as mensagens do sistema acima.'
    }
    Write-Host "Sistema disponivel em $url. Pressione Ctrl+C para encerrar."
    Start-Process $url
    $app.WaitForExit()
    exit $app.ExitCode
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
} finally {
    if ($app -and -not $app.HasExited) { Stop-Process -Id $app.Id }
}
