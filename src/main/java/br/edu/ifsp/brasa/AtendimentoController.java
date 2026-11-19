package br.edu.ifsp.brasa;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AtendimentoController {
    private final RestauranteRepository banco;
    private final RestauranteService servico;
    private final String senhaAdmin;

    public AtendimentoController(RestauranteRepository banco, RestauranteService servico,
                                 @Value("${totem.admin.senha}") String senhaAdmin) {
        this.banco = banco;
        this.servico = servico;
        this.senhaAdmin = senhaAdmin;
    }

    @GetMapping("/")
    public String cardapio(HttpSession sessao, Model model) {
        var produtos = banco.cardapio();
        var grupos = new LinkedHashMap<Integer, String>();
        produtos.forEach(produto -> grupos.put((Integer) produto.get("categoria"), (String) produto.get("grupo")));
        model.addAttribute("produtos", produtos);
        model.addAttribute("grupos", grupos);
        Integer id = cliente(sessao);
        if (id != null) {
            banco.clienteAtivo(id).ifPresent(dados -> model.addAttribute("cliente", dados.get("nome")));
        }
        return "cardapio";
    }

    @GetMapping("/conta")
    public String conta(@RequestParam(required = false) String modo, Model model) {
        model.addAttribute("cadastro", "cadastro".equals(modo));
        model.addAttribute("campos", Map.of());
        return "conta";
    }

    @PostMapping("/conta")
    public String entrar(@RequestParam Map<String, String> campos, HttpServletRequest request, Model model) {
        boolean cadastro = "cadastro".equals(campos.get("modo"));
        try {
            Integer id = cadastro ? Integer.valueOf(servico.cadastrar(campos))
                    : servico.autenticar(campos.getOrDefault("email", ""), campos.getOrDefault("senha", ""));
            if (id == null) throw new IllegalArgumentException("E-mail ou senha incorretos.");
            request.getSession().invalidate();
            request.getSession().setAttribute("cliente", id);
            return "redirect:/";
        } catch (IllegalArgumentException erro) {
            model.addAttribute("erro", erro.getMessage());
        } catch (DataIntegrityViolationException erro) {
            model.addAttribute("erro", "CPF ou e-mail já cadastrado.");
        }
        cliente(request.getSession());
        model.addAttribute("cadastro", cadastro);
        model.addAttribute("campos", Map.of(
                "nome", campos.getOrDefault("nome", ""),
                "cpf", campos.getOrDefault("cpf", ""),
                "email", campos.getOrDefault("email", "")));
        return "conta";
    }

    @GetMapping("/gestao/entrar")
    public String acessoEquipe(Model model) {
        model.addAttribute("admin", true);
        return "conta";
    }

    @PostMapping("/gestao/entrar")
    public String entrarEquipe(@RequestParam String senha, HttpServletRequest request, Model model) {
        if (!senhaAdmin.isBlank() && SegurancaWeb.iguais(senhaAdmin, senha)) {
            request.getSession().invalidate();
            request.getSession().setAttribute("admin", true);
            return "redirect:/gestao/produtos";
        }
        model.addAttribute("erro", "Acesso administrativo indisponível ou senha incorreta.");
        return acessoEquipe(model);
    }

    @PostMapping("/sair")
    public String sair(HttpSession sessao) {
        sessao.invalidate();
        return "redirect:/";
    }

    @PostMapping("/pedidos")
    public String pedir(@RequestParam Map<String, String> campos, HttpSession sessao, RedirectAttributes flash) {
        Integer id = cliente(sessao);
        try {
            var itens = new TreeMap<Integer, Integer>();
            for (var campo : campos.entrySet()) {
                if (campo.getKey().startsWith("item-")) {
                    itens.put(Integer.parseInt(campo.getKey().substring(5)), Integer.parseInt(campo.getValue()));
                }
            }
            int pedido = servico.pedir(id, itens);
            if (id == null) sessao.setAttribute("pedidoVisitante", pedido);
            flash.addFlashAttribute("sucesso", "Pedido confirmado!");
            flash.addFlashAttribute("confirmado", true);
            return "redirect:/pedidos/" + pedido;
        } catch (NumberFormatException erro) {
            flash.addFlashAttribute("erro", "Revise as quantidades do pedido.");
        } catch (IllegalArgumentException erro) {
            flash.addFlashAttribute("erro", erro.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/historico")
    public String historico(HttpSession sessao, Model model) {
        Integer id = cliente(sessao);
        if (id == null) return "redirect:/conta";
        model.addAttribute("pedidos", banco.pedidos(id));
        return "pedidos";
    }

    @GetMapping("/pedidos/{id}")
    public String detalhes(@PathVariable int id, HttpSession sessao, Model model) {
        boolean admin = Boolean.TRUE.equals(sessao.getAttribute("admin"));
        Integer cliente = admin ? null : cliente(sessao);
        boolean visitante = !admin && cliente == null;
        if (visitante && !Integer.valueOf(id).equals(sessao.getAttribute("pedidoVisitante"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("visitante", visitante);
        model.addAttribute("pedido", banco.pedido(id, cliente)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        model.addAttribute("itens", banco.itens(id));
        return "detalhes";
    }

    @PostMapping("/novo-pedido")
    public String novoPedido(HttpSession sessao) {
        sessao.removeAttribute("pedidoVisitante");
        return "redirect:/";
    }

    private Integer cliente(HttpSession sessao) {
        Integer id = (Integer) sessao.getAttribute("cliente");
        if (id != null && banco.clienteAtivo(id).isEmpty()) {
            sessao.removeAttribute("cliente");
            return null;
        }
        return id;
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String indisponivel(DataAccessException erro, Model model) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Falha na operação SQL", erro);
        model.addAttribute("mensagem", "Não foi possível acessar o banco agora. Tente novamente.");
        return "erro";
    }
}
