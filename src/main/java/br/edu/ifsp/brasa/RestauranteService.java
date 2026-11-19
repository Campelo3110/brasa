package br.edu.ifsp.brasa;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import br.edu.ifsp.brasa.RestauranteRepository.Registro;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestauranteService {
    private final RestauranteRepository banco;
    private final BCryptPasswordEncoder senhas = new BCryptPasswordEncoder();

    public RestauranteService(RestauranteRepository banco) {
        this.banco = banco;
    }

    public int cadastrar(Map<String, String> campos) {
        var dados = dadosCliente(campos, true);
        return banco.cadastrar((String) dados.get("Nome"), (String) dados.get("CPF"),
                (String) dados.get("E_mail"), (String) dados.get("Senha_Hash"));
    }

    public Integer autenticar(String email, String senha) {
        return banco.credenciais(email.strip().toLowerCase(Locale.ROOT))
                .filter(dados -> senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
                        && senhas.matches(senha, (String) dados.get("hash")))
                .map(dados -> (Integer) dados.get("id")).orElse(null);
    }

    @Transactional
    public int pedir(Integer cliente, Map<Integer, Integer> itens) {
        if (itens.isEmpty() || itens.size() > 100) {
            throw new IllegalArgumentException("Inclua de 1 a 100 produtos diferentes no pedido.");
        }
        if (cliente != null && banco.clienteAtivo(cliente).isEmpty()) {
            throw new IllegalArgumentException("Identifique-se para confirmar o pedido.");
        }
        int pedido = banco.abrirPedido(cliente);
        for (var item : itens.entrySet()) {
            int quantidade = item.getValue();
            if (quantidade < 1 || quantidade > 99) {
                throw new IllegalArgumentException("Revise as quantidades do pedido.");
            }
            if (!banco.incluirItem(pedido, item.getKey(), quantidade)) {
                throw new IllegalArgumentException("Um produto ficou indisponível. Revise o carrinho.");
            }
        }
        banco.finalizar(pedido);
        return pedido;
    }

    public void salvar(Registro tipo, int id, Map<String, String> campos) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("Nome", texto(campos, "nome", "Informe o nome."));
        boolean ativo = "1".equals(campos.get("ativo"));
        switch (tipo) {
            case clientes -> {
                dados.putAll(dadosCliente(campos, id == 0));
                dados.put("Ativo", ativo);
            }
            case categorias -> dados.put("Ativo", ativo);
            case produtos -> {
                dados.put("Descricao", texto(campos, "descricao", "Informe a descrição."));
                BigDecimal preco;
                int categoria;
                try {
                    preco = new BigDecimal(campos.getOrDefault("preco", "").replace(',', '.')).stripTrailingZeros();
                    categoria = Integer.parseInt(campos.getOrDefault("categoria", ""));
                    if (preco.signum() <= 0 || preco.scale() > 2 || preco.compareTo(new BigDecimal("99999999.99")) > 0 || categoria <= 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException erro) {
                    throw new IllegalArgumentException("Informe uma categoria e um preço válido com até duas casas decimais.");
                }
                String disponibilidade = campos.getOrDefault("disponibilidade", "");
                if (!disponibilidade.equals("Disponível") && !disponibilidade.equals("Indisponível")) {
                    throw new IllegalArgumentException("Selecione a disponibilidade.");
                }
                dados.put("Preco", preco.setScale(2));
                dados.put("Disponibilidade", disponibilidade);
                dados.put("FK_ID_Categoria", categoria);
            }
        }
        banco.salvar(tipo, id, dados);
    }

    @Transactional
    public boolean remover(Registro tipo, int id) {
        return banco.remover(tipo, id);
    }

    private Map<String, Object> dadosCliente(Map<String, String> campos, boolean senhaObrigatoria) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("Nome", texto(campos, "nome", "Informe o nome."));
        String cpf = campos.getOrDefault("cpf", "").replaceAll("[.\\s-]", "");
        if (!cpf.matches("[0-9]{11}")) throw new IllegalArgumentException("Informe os 11 dígitos do CPF.");
        dados.put("CPF", cpf);
        String email = texto(campos, "email", "Informe um e-mail válido.").toLowerCase(Locale.ROOT);
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        dados.put("E_mail", email);
        String senha = campos.getOrDefault("senha", "");
        if (senhaObrigatoria || !senha.isEmpty()) {
            if (senha.length() < 8 || senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                throw new IllegalArgumentException("A senha deve ter pelo menos 8 caracteres e até 72 bytes.");
            }
            dados.put("Senha_Hash", senhas.encode(senha));
        }
        return dados;
    }

    private String texto(Map<String, String> campos, String chave, String mensagem) {
        String texto = campos.getOrDefault(chave, "").strip();
        if (texto.isEmpty() || texto.length() > 255) throw new IllegalArgumentException(mensagem);
        return texto;
    }
}
