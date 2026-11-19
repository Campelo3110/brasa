package br.edu.ifsp.brasa;

import java.util.Map;

import br.edu.ifsp.brasa.RestauranteRepository.Registro;
import jakarta.servlet.http.HttpSession;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/gestao")
public class GestaoController {
    private final RestauranteRepository banco;
    private final RestauranteService servico;

    public GestaoController(RestauranteRepository banco, RestauranteService servico) {
        this.banco = banco;
        this.servico = servico;
    }

    @GetMapping("/{tipo}")
    public String listar(@PathVariable String tipo, @RequestParam(defaultValue = "0") int editar,
                        HttpSession sessao, Model model) {
        if (!admin(sessao)) return "redirect:/gestao/entrar";
        model.addAttribute("tipo", tipo);
        if (tipo.equals("pedidos")) {
            model.addAttribute("pedidos", banco.pedidos(null));
            model.addAttribute("admin", true);
            return "pedidos";
        }
        Registro tabela = tabela(tipo);
        var registros = banco.listar(tabela);
        var selecionado = registros.stream().filter(item -> item.get("id").equals(editar)).findFirst();
        model.addAttribute("registros", registros);
        model.addAttribute("registro", selecionado.orElse(Map.of()));
        model.addAttribute("categorias", tipo.equals("produtos") ? banco.listar(Registro.categorias) : java.util.List.of());
        return "gestao";
    }

    @PostMapping("/{tipo}")
    public String alterar(@PathVariable String tipo, @RequestParam(defaultValue = "0") int id,
                          @RequestParam Map<String, String> campos, HttpSession sessao, RedirectAttributes flash) {
        if (!admin(sessao)) return "redirect:/gestao/entrar";
        Registro tabela = tabela(tipo);
        try {
            if (id < 0) throw new IllegalArgumentException("Registro inválido.");
            if ("remover".equals(campos.get("acao"))) {
                boolean inativado = servico.remover(tabela, id);
                flash.addFlashAttribute("sucesso", inativado ? "Registro desativado. Os vínculos foram preservados." : "Registro removido.");
            } else {
                servico.salvar(tabela, id, campos);
                flash.addFlashAttribute("sucesso", "Alteração registrada.");
            }
        } catch (IllegalArgumentException erro) {
            flash.addFlashAttribute("erro", erro.getMessage());
        } catch (DataIntegrityViolationException erro) {
            flash.addFlashAttribute("erro", "Verifique se os dados já existem ou se a categoria ainda está cadastrada.");
        }
        return "redirect:/gestao/" + tipo;
    }

    private boolean admin(HttpSession sessao) {
        return Boolean.TRUE.equals(sessao.getAttribute("admin"));
    }

    private Registro tabela(String tipo) {
        try {
            return Registro.valueOf(tipo);
        } catch (IllegalArgumentException erro) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public String indisponivel(DataAccessException erro, Model model) {
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Falha na operação SQL", erro);
        model.addAttribute("mensagem", "Não foi possível acessar o banco agora. Tente novamente.");
        return "erro";
    }
}
