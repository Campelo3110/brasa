# Brasa de Rua

Sistema web de autoatendimento para restaurantes. O cliente navega pelo cardápio, monta o pedido, confirma a compra sem conta e recebe o número para retirada. O cliente identificado pode repetir um pedido do histórico. A equipe gerencia clientes, categorias e produtos e consulta os pedidos registrados.

## Executar no Windows com dois cliques

1. Instale o JDK 21, deixe `java` no PATH e instale o SQL Server Express. Deixe o serviço do SQL Server iniciado, com autenticação mista e TCP/IP habilitados na porta 1433. A conta do Windows precisa poder criar bancos e logins nessa instância.
2. Extraia a pasta completa do projeto e abra `Executar.cmd`.
3. Digite a senha que deseja usar na **Área da equipe** durante essa execução. Aguarde o cardápio abrir no navegador em <http://127.0.0.1:5000/>. Para encerrar, pressione **Ctrl+C** na janela do terminal.

O inicializador executa os scripts SQL, configura um login próprio da aplicação e compila o projeto. O primeiro build precisa de internet para baixar o Maven e as dependências. O Windows guarda a credencial SQL gerada em `%LOCALAPPDATA%\BrasaDeRua`, vinculada ao usuário e ao computador. Se sua instância usar outra porta, defina `BRASA_SQL_PORTA` antes de abrir o inicializador.

## Executar com configuração manual

Pré-requisitos: JDK 21, SQL Server 2022 Express ou edição equivalente com TCP/IP e uma ferramenta SQL, como o SSMS.

1. No SQL Server Configuration Manager, habilite TCP/IP para a instância e configure uma porta fixa, por exemplo, 1433. Reinicie o serviço da instância.
2. No SSMS, execute `banco/01-estrutura.sql` e `banco/02-cardapio.sql` na ordem, com uma conta autorizada a criar o banco.
3. Com a autenticação mista habilitada na instância, crie um login para a aplicação. Substitua o valor da senha antes de executar o exemplo.

```sql
USE master;
IF SUSER_ID('totem_app') IS NULL
    CREATE LOGIN totem_app WITH PASSWORD = 'SUBSTITUA_POR_UMA_SENHA_FORTE';
ELSE
    ALTER LOGIN totem_app WITH PASSWORD = 'SUBSTITUA_POR_UMA_SENHA_FORTE';
GO
USE Totem_Atendimento;
IF USER_ID('totem_app') IS NULL CREATE USER totem_app FOR LOGIN totem_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::dbo TO totem_app;
GRANT EXECUTE ON OBJECT::dbo.FinalizarPedido TO totem_app;
GO
```

4. Abra o PowerShell nesta pasta, informe as senhas escolhidas e inicie o projeto.

```powershell
$env:TOTEM_SQL_SENHA = 'senha-do-login-sql'
$env:TOTEM_ADMIN_SENHA = 'senha-da-equipe'
.\mvnw.cmd spring-boot:run
```

O Maven Wrapper baixa o Maven na primeira execução. Em Linux/macOS, use `sh ./mvnw spring-boot:run`. Abra <http://127.0.0.1:5000>. Confirme um pedido como visitante ou entre em uma conta para usar o histórico. Use **Área da equipe** com a senha configurada para acessar a gestão.

A configuração padrão espera `totem_app` em `localhost:1433`. Para mudar a conexão, defina `TOTEM_SQL_USUARIO` e/ou `TOTEM_SQL_URL` (exemplo `jdbc:sqlserver://localhost:1435;databaseName=Totem_Atendimento;encrypt=true;trustServerCertificate=true`). Em uma implantação com certificado confiável, use `trustServerCertificate=false` e habilite `TOTEM_HTTPS=true` sob HTTPS.

Para atualizar um banco existente, execute novamente `banco/01-estrutura.sql`. O vínculo com o cliente passa a ser opcional e os pedidos existentes são preservados.

## Estrutura

- `src/main/java` contém a aplicação Java 21 com Spring Boot MVC, regras do pedido, acesso a dados via Spring JDBC e controle de sessão.
- `src/main/resources/templates` contém as telas Thymeleaf. CSS, JavaScript e fotos ficam em `src/main/resources/static`.
- `banco/` contém tabelas, índices, procedure, função, view, trigger e carga do cardápio em T-SQL.
- O [relatório do projeto](docs/Projeto%20de%20software.pdf) reúne os modelos, casos de uso, resultados de validação e proposta comercial. A [versão editável](docs/projeto.md) está em Markdown.

As fotos demonstrativas são do [Unsplash](https://unsplash.com/), distribuídas no projeto para funcionamento local.
