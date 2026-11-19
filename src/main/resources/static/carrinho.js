const chave = "brasa-carrinho";

function lerCarrinho() {
  try {
    return JSON.parse(sessionStorage.getItem(chave)) || {};
  } catch {
    return {};
  }
}

document.querySelector("[data-sair]")?.addEventListener("submit", () => sessionStorage.removeItem(chave));
if (document.querySelector("[data-confirmado]")) sessionStorage.removeItem(chave);

document.querySelector("[data-repetir]")?.addEventListener("click", () => {
  const itens = Array.from(document.querySelectorAll(".itens-detalhe [data-produto]"));
  const pedido = Object.fromEntries(itens.map(({ dataset }) => [dataset.produto, Number(dataset.quantidade)]));
  sessionStorage.setItem(chave, JSON.stringify(pedido));
  sessionStorage.setItem("brasa-recompra", "1");
});

document.querySelectorAll("[data-remover]").forEach((formulario) => {
  formulario.addEventListener("submit", (evento) => {
    if (!confirm("Remover este registro do uso ativo?")) evento.preventDefault();
  });
});

const formulario = document.querySelector("#form-pedido");
if (formulario) prepararCardapio();

function prepararCardapio() {
  const cartoes = [...document.querySelectorAll("[data-produto]")];
  const produtos = new Map(cartoes.map((card) => [card.dataset.produto, card.dataset]));
  const filtros = document.querySelectorAll("[data-filtro]");
  const itens = document.querySelector("#itens-carrinho");
  const campos = document.querySelector("#campos-itens");
  const contador = document.querySelector("#contador");
  const confirmar = document.querySelector("#confirmar");
  const aviso = document.querySelector("#carrinho-aviso");
  const preco = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
  const carrinho = lerCarrinho();
  let indisponivel = false;

  for (const id of Object.keys(carrinho)) {
    if (!produtos.has(id) || !Number.isInteger(carrinho[id]) || carrinho[id] < 1 || carrinho[id] > 99) {
      delete carrinho[id];
      indisponivel = true;
    }
  }
  if (sessionStorage.getItem("brasa-recompra")) {
    aviso.textContent = "Recompra carregada com os preços atuais.";
    sessionStorage.removeItem("brasa-recompra");
  }
  if (indisponivel) aviso.textContent = "Itens indisponíveis foram retirados do pedido.";

  const atalho = document.createElement("a");
  atalho.href = "#carrinho";
  atalho.className = "botao primario atalho-carrinho";
  formulario.closest("main").append(atalho);

  function mudar(id, delta, foco) {
    const novo = (carrinho[id] || 0) + delta;
    if (novo > 99) {
      aviso.textContent = "O limite é de 99 unidades por produto.";
      return;
    }
    if (novo === 0) delete carrinho[id];
    else carrinho[id] = novo;
    aviso.textContent = produtos.get(id).nome + (delta > 0 ? " adicionado." : " removido.");
    desenhar();
    if (foco) {
      const botao = itens.querySelector('[data-ajuste="' + id + ':' + delta + '"]');
      (botao || atalho).focus();
    }
  }

  function desenhar() {
    itens.replaceChildren();
    campos.replaceChildren();
    let total = 0;
    let quantidadeTotal = 0;
    for (const [id, quantidade] of Object.entries(carrinho)) {
      const produto = produtos.get(id);
      total += Number(produto.centavos) * quantidade;
      quantidadeTotal += quantidade;

      const linha = document.createElement("div");
      linha.className = "item-carrinho";
      const nome = document.createElement("strong");
      nome.textContent = produto.nome;
      const controles = document.createElement("div");
      controles.className = "quantidade";
      const numero = document.createElement("span");
      numero.textContent = quantidade;
      for (const delta of [-1, 1]) {
        const botao = document.createElement("button");
        botao.type = "button";
        botao.textContent = delta < 0 ? "−" : "+";
        botao.dataset.ajuste = id + ":" + delta;
        botao.setAttribute("aria-label", (delta < 0 ? "Remover " : "Adicionar ") + produto.nome);
        botao.addEventListener("click", () => mudar(id, delta, true));
        if (delta > 0) controles.append(numero);
        controles.append(botao);
      }
      linha.append(nome, controles);
      itens.append(linha);

      const campo = document.createElement("input");
      campo.type = "hidden";
      campo.name = "item-" + id;
      campo.value = quantidade;
      campos.append(campo);
    }
    if (!quantidadeTotal) {
      const vazio = document.createElement("p");
      vazio.textContent = "Adicione algum produto do cardápio.";
      itens.append(vazio);
    }
    contador.textContent = quantidadeTotal + (quantidadeTotal === 1 ? " item" : " itens");
    document.querySelector("#total").textContent = preco.format(total / 100);
    atalho.textContent = "Seu pedido · " + contador.textContent;
    atalho.hidden = quantidadeTotal === 0;
    if (confirmar) confirmar.disabled = quantidadeTotal === 0;
    sessionStorage.setItem(chave, JSON.stringify(carrinho));
  }

  for (const cartao of cartoes) {
    cartao.querySelector(".adicionar").addEventListener("click", () => mudar(cartao.dataset.produto, 1));
  }
  filtros.forEach((filtro) => filtro.addEventListener("click", () => {
    filtros.forEach((item) => item.setAttribute("aria-pressed", item === filtro));
    const grupo = filtro.dataset.filtro;
    cartoes.forEach((card) => { card.hidden = grupo !== "todos" && card.dataset.categoria !== grupo; });
  }));
  formulario.addEventListener("submit", () => {
    if (confirmar) {
      confirmar.disabled = true;
      confirmar.textContent = "Confirmando…";
    }
  });
  desenhar();
}

