document.querySelectorAll(".seletor-imagem-evento").forEach((input) => {
    const preview = document.getElementById(input.dataset.previewId);
    const nomes = document.getElementById(input.dataset.nomesId);
    const textoVazio = nomes.textContent;
    const limite = input.id === "capa" ? 1 : 5;
    let urlsPreview = [];

    const renderizar = () => {
        urlsPreview.forEach((url) => URL.revokeObjectURL(url));
        urlsPreview = [];
        preview.replaceChildren();

        const arquivos = Array.from(input.files);
        nomes.textContent = arquivos.length
            ? arquivos.map((arquivo) => arquivo.name).join(", ")
            : textoVazio;

        const arquivoInvalido = arquivos.find((arquivo) =>
            !["image/jpeg", "image/png", "image/gif"].includes(arquivo.type)
            || arquivo.size > 5 * 1024 * 1024
        );

        if (arquivos.length > limite) {
            input.setCustomValidity(`Selecione no máximo ${limite} ${limite === 1 ? "imagem" : "imagens"}.`);
        } else if (arquivoInvalido) {
            input.setCustomValidity("Use JPG, PNG ou GIF de até 5 MB cada.");
        } else {
            input.setCustomValidity("");
        }

        arquivos.filter((arquivo) => arquivo.type.startsWith("image/")).forEach((arquivo, indice) => {
            const item = document.createElement("div");
            item.className = "imagem-preview-item";

            const imagem = document.createElement("img");
            const url = URL.createObjectURL(arquivo);
            urlsPreview.push(url);
            imagem.src = url;
            imagem.alt = arquivo.name;

            const remover = document.createElement("button");
            remover.className = "imagem-remover";
            remover.type = "button";
            remover.textContent = "🗑";
            remover.setAttribute("aria-label", `Remover ${arquivo.name}`);
            remover.addEventListener("click", () => {
                const arquivosAtualizados = Array.from(input.files);
                arquivosAtualizados.splice(indice, 1);
                const dados = new DataTransfer();
                arquivosAtualizados.forEach((arquivoAtualizado) => dados.items.add(arquivoAtualizado));
                input.files = dados.files;
                renderizar();
            });

            item.append(imagem, remover);
            preview.append(item);
        });
    };

    input.addEventListener("change", renderizar);
});

document.querySelectorAll("[data-imagem-atual]").forEach((item) => {
    const checkbox = item.querySelector(".imagem-remover-flag");
    const botao = item.querySelector("[data-marcar-remocao]");

    botao.addEventListener("click", () => {
        checkbox.checked = !checkbox.checked;
        item.classList.toggle("marcada-para-exclusao", checkbox.checked);
        botao.textContent = checkbox.checked ? "↶" : "🗑";
        botao.setAttribute(
            "aria-label",
            checkbox.checked ? "Desfazer exclusão da foto" : "Excluir foto"
        );
    });
});
