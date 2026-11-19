USE Totem_Atendimento;
GO
IF NOT EXISTS (SELECT 1 FROM Categoria)
BEGIN
    INSERT Categoria (Nome) VALUES (N'Hambúrgueres'), (N'Acompanhamentos'), (N'Bebidas');
    INSERT Produto (Nome, Descricao, Preco, Disponibilidade, FK_ID_Categoria, Foto)
    SELECT v.Nome, v.Descricao, v.Preco, N'Disponível', c.ID_Categoria, v.Foto
    FROM (VALUES
        (N'Clássico da Brasa', N'Carne na brasa, cheddar, alface e molho da casa.', 29.90, N'Hambúrgueres', 'classico.jpg'),
        (N'Bacon Defumado', N'Cheddar, bacon crocante e cebola caramelizada.', 34.90, N'Hambúrgueres', 'bacon.jpg'),
        (N'Duplo Cheddar', N'Dois hambúrgueres, cheddar e picles no brioche.', 38.90, N'Hambúrgueres', 'duplo.jpg'),
        (N'Batata Frita', N'Batatas douradas com sal e molho da casa.', 16.90, N'Acompanhamentos', 'batata.jpg'),
        (N'Anéis de Cebola', N'Porção de anéis de cebola empanados.', 18.90, N'Acompanhamentos', 'cebola.jpg'),
        (N'Limonada da Casa', N'Limão, gelo e hortelã fresca.', 11.90, N'Bebidas', 'limonada.jpg')
    ) v(Nome, Descricao, Preco, Categoria, Foto)
    JOIN Categoria c ON c.Nome = v.Categoria;
END;
GO
