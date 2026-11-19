package br.edu.ifsp.brasa;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RestauranteRepository {
    private final JdbcTemplate jdbc;

    public RestauranteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> cardapio() {
        return jdbc.queryForList("""
                SELECT p.ID_Produto AS id, p.Nome AS nome, p.Descricao AS descricao,
                    p.Preco AS preco, p.Foto AS foto, c.ID_Categoria AS categoria, c.Nome AS grupo
                FROM Produto p JOIN Categoria c ON c.ID_Categoria=p.FK_ID_Categoria
                WHERE p.Disponibilidade=N'Disponível' AND c.Ativo=1
                ORDER BY c.ID_Categoria, p.ID_Produto
                """);
    }

    public Optional<Map<String, Object>> clienteAtivo(int id) {
        return jdbc.queryForList("SELECT Nome AS nome FROM Cliente WHERE ID_Cliente=? AND Ativo=1", id)
                .stream().findFirst();
    }

    public Optional<Map<String, Object>> credenciais(String email) {
        return jdbc.queryForList("""
                SELECT ID_Cliente AS id, Senha_Hash AS hash FROM Cliente WHERE E_mail=? AND Ativo=1
                """, email).stream().findFirst();
    }

    public int cadastrar(String nome, String cpf, String email, String senha) {
        return jdbc.queryForObject("""
                INSERT Cliente (Nome, CPF, E_mail, Senha_Hash)
                OUTPUT inserted.ID_Cliente VALUES (?, ?, ?, ?)
                """, Integer.class, nome, cpf, email, senha);
    }

    public int abrirPedido(Integer cliente) {
        return jdbc.queryForObject("""
                INSERT Pedido (Status_Pedido, FK_ID_Cliente) OUTPUT inserted.ID_Pedido
                VALUES ('Pendente', ?)
                """, Integer.class, cliente);
    }

    public boolean incluirItem(int pedido, int produto, int quantidade) {
        return jdbc.update("""
                INSERT Item_Pedido (Quantidade, Preco_Unitario, Nome_Produto, FK_ID_Pedido, FK_ID_Produto)
                SELECT ?, p.Preco, p.Nome, ?, p.ID_Produto
                FROM Produto p JOIN Categoria c ON c.ID_Categoria=p.FK_ID_Categoria
                WHERE p.ID_Produto=? AND c.Ativo=1 AND p.Disponibilidade=N'Disponível'
                """, quantidade, pedido, produto) == 1;
    }

    public void finalizar(int pedido) {
        jdbc.update("EXEC dbo.FinalizarPedido ?", pedido);
    }

    private static final String RESUMO = """
            SELECT r.ID_Pedido AS id, r.Data_Hora AS data, r.Status_Pedido AS status,
                r.Total AS total, COALESCE(c.Nome, N'Visitante') AS cliente
            FROM dbo.ResumoPedido r LEFT JOIN Cliente c ON c.ID_Cliente=r.FK_ID_Cliente
            """;

    public List<Map<String, Object>> pedidos(Integer cliente) {
        String ordem = " ORDER BY r.ID_Pedido DESC";
        return cliente == null ? jdbc.queryForList(RESUMO + ordem)
                : jdbc.queryForList(RESUMO + " WHERE r.FK_ID_Cliente=?" + ordem, cliente);
    }

    public Optional<Map<String, Object>> pedido(int pedido, Integer cliente) {
        String filtro = " WHERE r.ID_Pedido=?";
        List<Map<String, Object>> linhas = cliente == null ? jdbc.queryForList(RESUMO + filtro, pedido)
                : jdbc.queryForList(RESUMO + filtro + " AND r.FK_ID_Cliente=?", pedido, cliente);
        return linhas.stream().findFirst();
    }

    public List<Map<String, Object>> itens(int pedido) {
        return jdbc.queryForList("""
                SELECT Nome_Produto AS nome, Quantidade AS quantidade, Preco_Unitario AS preco,
                    CAST(Quantidade * Preco_Unitario AS DECIMAL(19,2)) AS subtotal,
                    FK_ID_Produto AS produto
                FROM Item_Pedido WHERE FK_ID_Pedido=? ORDER BY ID_Item
                """, pedido);
    }

    public enum Registro {
        clientes("Cliente", "ID_Cliente", "Nome AS nome, CPF AS cpf, E_mail AS email, Ativo AS ativo",
                "Pedido", "FK_ID_Cliente", "Ativo=0"),
        categorias("Categoria", "ID_Categoria", "Nome AS nome, Ativo AS ativo",
                "Produto", "FK_ID_Categoria", "Ativo=0"),
        produtos("Produto", "ID_Produto", """
                Nome AS nome, Descricao AS descricao, Preco AS preco, Disponibilidade AS disponibilidade,
                FK_ID_Categoria AS categoria
                """, "Item_Pedido", "FK_ID_Produto", "Disponibilidade=N'Indisponível'");

        private final String tabela, chave, campos, dependencia, fk, inativacao;

        Registro(String tabela, String chave, String campos, String dependencia, String fk, String inativacao) {
            this.tabela = tabela;
            this.chave = chave;
            this.campos = campos;
            this.dependencia = dependencia;
            this.fk = fk;
            this.inativacao = inativacao;
        }
    }

    public List<Map<String, Object>> listar(Registro tipo) {
        return jdbc.queryForList("SELECT " + tipo.chave + " AS id, " + tipo.campos
                + " FROM " + tipo.tabela + " ORDER BY Nome");
    }

    public void salvar(Registro tipo, int id, Map<String, Object> valores) {
        String campos = String.join(", ", valores.keySet());
        var parametros = new java.util.ArrayList<>(valores.values());
        if (id == 0) {
            String marcadores = String.join(", ", java.util.Collections.nCopies(valores.size(), "?"));
            jdbc.update("INSERT " + tipo.tabela + " (" + campos + ") VALUES (" + marcadores + ")", parametros.toArray());
            return;
        }
        String atribuicoes = String.join(", ", valores.keySet().stream().map(campo -> campo + "=?").toList());
        parametros.add(id);
        int alterados = jdbc.update("UPDATE " + tipo.tabela + " SET " + atribuicoes + " WHERE " + tipo.chave + "=?",
                parametros.toArray());
        if (alterados == 0) throw new IllegalArgumentException("Registro não encontrado.");
    }

    public boolean remover(Registro tipo, int id) {
        String onde = " WHERE " + tipo.chave + "=?";
        boolean possuiDependencia = !jdbc.queryForList(
                "SELECT TOP (1) 1 FROM " + tipo.dependencia + " WITH (HOLDLOCK) WHERE " + tipo.fk + "=?", id).isEmpty();
        int alterados = possuiDependencia
                ? jdbc.update("UPDATE " + tipo.tabela + " SET " + tipo.inativacao + onde, id)
                : jdbc.update("DELETE FROM " + tipo.tabela + onde, id);
        if (alterados == 0) throw new IllegalArgumentException("Registro não encontrado.");
        return possuiDependencia;
    }
}

