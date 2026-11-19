IF DB_ID(N'Totem_Atendimento') IS NULL CREATE DATABASE Totem_Atendimento;
GO
USE Totem_Atendimento;
GO
IF OBJECT_ID('dbo.Cliente') IS NULL
BEGIN
    CREATE TABLE Cliente (
        ID_Cliente INT IDENTITY PRIMARY KEY,
        Nome NVARCHAR(255) NOT NULL,
        CPF CHAR(11) NOT NULL UNIQUE CHECK (CPF NOT LIKE '%[^0-9]%'),
        E_mail NVARCHAR(255) NOT NULL UNIQUE,
        Senha_Hash VARCHAR(255) NOT NULL,
        Ativo BIT NOT NULL DEFAULT 1,
        Data_Cadastro DATE NOT NULL DEFAULT GETDATE()
    );
    CREATE TABLE Categoria (
        ID_Categoria INT IDENTITY PRIMARY KEY,
        Nome NVARCHAR(255) NOT NULL UNIQUE,
        Ativo BIT NOT NULL DEFAULT 1
    );
    CREATE TABLE Produto (
        ID_Produto INT IDENTITY PRIMARY KEY,
        Nome NVARCHAR(255) NOT NULL,
        Descricao NVARCHAR(255) NOT NULL,
        Preco DECIMAL(10,2) NOT NULL CHECK (Preco > 0),
        Disponibilidade NVARCHAR(15) NOT NULL CHECK (Disponibilidade IN (N'Disponível', N'Indisponível')),
        FK_ID_Categoria INT NOT NULL REFERENCES Categoria,
        Foto VARCHAR(40) NULL
    );
    CREATE TABLE Pedido (
        ID_Pedido INT IDENTITY PRIMARY KEY,
        Data_Hora DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
        Status_Pedido VARCHAR(10) NOT NULL CHECK (Status_Pedido IN ('Pendente', 'Finalizado')),
        FK_ID_Cliente INT NULL REFERENCES Cliente
    );
    CREATE TABLE Item_Pedido (
        ID_Item INT IDENTITY PRIMARY KEY,
        Quantidade INT NOT NULL CHECK (Quantidade BETWEEN 1 AND 99),
        Preco_Unitario DECIMAL(10,2) NOT NULL CHECK (Preco_Unitario > 0),
        Nome_Produto NVARCHAR(255) NOT NULL,
        FK_ID_Pedido INT NOT NULL REFERENCES Pedido,
        FK_ID_Produto INT NOT NULL REFERENCES Produto,
        UNIQUE (FK_ID_Pedido, FK_ID_Produto)
    );
    CREATE INDEX IX_Pedido_Cliente ON Pedido(FK_ID_Cliente, ID_Pedido DESC);
    CREATE INDEX IX_Produto_Categoria ON Produto(FK_ID_Categoria);
    CREATE INDEX IX_Item_Produto ON Item_Pedido(FK_ID_Produto);
END;
GO
IF COLUMNPROPERTY(OBJECT_ID(N'dbo.Pedido'), 'FK_ID_Cliente', 'AllowsNull') = 0
    ALTER TABLE dbo.Pedido ALTER COLUMN FK_ID_Cliente INT NULL;
GO
CREATE OR ALTER FUNCTION dbo.TotalPedido(@pedido INT)
RETURNS DECIMAL(19,2)
AS
BEGIN
    RETURN (SELECT COALESCE(SUM(Quantidade * Preco_Unitario), 0)
            FROM Item_Pedido WHERE FK_ID_Pedido = @pedido);
END;
GO
CREATE OR ALTER VIEW dbo.ResumoPedido AS
SELECT p.ID_Pedido, p.Data_Hora, p.Status_Pedido, p.FK_ID_Cliente,
       COALESCE(SUM(i.Quantidade * i.Preco_Unitario), 0) AS Total
FROM Pedido p LEFT JOIN Item_Pedido i ON i.FK_ID_Pedido = p.ID_Pedido
GROUP BY p.ID_Pedido, p.Data_Hora, p.Status_Pedido, p.FK_ID_Cliente;
GO
CREATE OR ALTER TRIGGER dbo.ProtegerItensFinalizados ON dbo.Item_Pedido
AFTER INSERT, UPDATE, DELETE AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM Pedido p
        WHERE p.Status_Pedido = 'Finalizado'
          AND p.ID_Pedido IN (SELECT FK_ID_Pedido FROM deleted UNION SELECT FK_ID_Pedido FROM inserted)
    )
        THROW 50001, N'Os itens de pedidos finalizados são imutáveis.', 1;
END;
GO
CREATE OR ALTER PROCEDURE dbo.FinalizarPedido @pedido INT AS
BEGIN
    SET NOCOUNT ON;
    IF dbo.TotalPedido(@pedido) <= 0
        THROW 50002, N'O pedido precisa de ao menos um item.', 1;
    UPDATE Pedido SET Status_Pedido = 'Finalizado'
    WHERE ID_Pedido = @pedido AND Status_Pedido = 'Pendente';
    IF @@ROWCOUNT = 0 THROW 50003, N'Pedido pendente não encontrado.', 1;
END;
GO
