package com.example.demo;

import jakarta.servlet.http.HttpSession;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

@Controller
public class EventoController {

    private final EventoRepository eventos;
    private final ImagemEventoService imagens;

    private final UsuarioRepository usuarios;
    private final IngressoRepository ingressos;

    public EventoController(EventoRepository eventos, ImagemEventoService imagens, UsuarioRepository usuarios,
                            IngressoRepository ingressos) {
        this.eventos = eventos;
        this.imagens = imagens;   
        this.usuarios = usuarios;
        this.ingressos = ingressos;
    }

    @GetMapping("/")
    public String inicio() {
        return "inicio";
    }

    @GetMapping("/sac")
    public String sac() {
        return "sac";
    }

    @GetMapping("/mural")
    public String mural(HttpSession session, Model model) {
        model.addAttribute("eventos", eventos.findAllByOrderByDataHoraAsc());
        model.addAttribute("usuarioNome", session.getAttribute("usuarioNome"));
        model.addAttribute("usuarioEmail", session.getAttribute("usuarioEmail"));
        return "index";
    }

    @GetMapping("/meus-eventos")
    @Transactional
    public String meusEventos(HttpSession session, Model model) {
        Usuario usuarioSessao = (Usuario) session.getAttribute("usuarioLogado");
        if (usuarioSessao == null) {
            return "redirect:/login";
        }

        Usuario usuario = usuarios.findById(usuarioSessao.getId()).orElse(null);
        if (usuario == null) {
            session.invalidate();
            return "redirect:/login";
        }

        List<Evento> eventosPublicados = eventos.findAllByOrderByDataHoraAsc();
        List<Ingresso> ingressosUsuario = eventosPublicados.stream()
                .filter(evento -> evento.isParticipante(usuario))
                .map(evento -> ingressos.findByEvento_IdAndUsuario_Id(evento.getId(), usuario.getId())
                        .orElseGet(() -> ingressos.save(new Ingresso(evento, usuario))))
                .toList();
        List<Evento> eventosCriados = eventosPublicados.stream()
                .filter(evento -> evento.foiCriadoPor(usuario.getEmail()))
                .toList();

        model.addAttribute("ingressos", ingressosUsuario);
        model.addAttribute("eventosCriados", eventosCriados);
        model.addAttribute("usuarioNome", usuario.getNome());
        return "meus-eventos";
    }

    @GetMapping("/ingressos/{token}/qr")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> qrIngresso(@PathVariable String token, HttpSession session)
            throws WriterException, IOException {
        Usuario usuario = (Usuario) session.getAttribute("usuarioLogado");
        Ingresso ingresso = usuario == null || usuario.getId() == null
                ? null
                : ingressos.findByTokenAndUsuario_Id(token, usuario.getId()).orElse(null);
        if (ingresso == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] qrPng = gerarQrPng(construirUrlValidacaoIngresso(ingresso.getToken()));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Cache-Control", "no-store")
                .body(qrPng);
    }

    @GetMapping("/ingressos/{token}/baixar")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> baixarIngresso(@PathVariable String token, HttpSession session)
            throws WriterException, IOException {
        Usuario sessao = (Usuario) session.getAttribute("usuarioLogado");
        Usuario usuario = sessao == null || sessao.getId() == null
                ? null
                : usuarios.findById(sessao.getId()).orElse(null);
        Ingresso ingresso = usuario == null
                ? null
                : ingressos.findByTokenAndUsuario_Id(token, usuario.getId()).orElse(null);
        if (ingresso == null) {
            return ResponseEntity.notFound().build();
        }
        if (usuario.getCpf() == null || usuario.getCpf().isBlank()) {
            session.setAttribute("erroCpf", "Cadastre seu CPF para baixar o ingresso.");
            session.setAttribute("ingressoPendenteCpf", ingresso.getToken());
            return ResponseEntity.status(303)
                    .header(HttpHeaders.LOCATION, "/perfil")
                    .build();
        }

        byte[] pdf = gerarPdfIngresso(ingresso, usuario);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ingresso-mwk.pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf);
    }

    private byte[] gerarPdfIngresso(Ingresso ingresso, Usuario usuario)
            throws IOException, WriterException {
        byte[] qrPng = gerarQrPng(construirUrlValidacaoIngresso(ingresso.getToken()));
        try (PDDocument documento = new PDDocument();
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDRectangle tamanhoPagina = new PDRectangle(420, 860);
            PDPage pagina = new PDPage(tamanhoPagina);
            documento.addPage(pagina);
            float margem = 32;
            float larguraTexto = tamanhoPagina.getWidth() - margem * 2;
            float y = tamanhoPagina.getHeight() - 42;
            PDType1Font fonteNormal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font fonteNegrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                y = escreverLinhasPdf(conteudo, fonteNegrito, 11, "MWK EVENTOS  |  INGRESSO",
                        margem, y, larguraTexto, 16, new java.awt.Color(142, 28, 40));
                y -= 14;
                y = escreverLinhasPdf(conteudo, fonteNegrito, 25, ingresso.getEvento().getNome(),
                        margem, y, larguraTexto, 30, new java.awt.Color(40, 34, 32));
                y -= 4;
                y = escreverLinhasPdf(conteudo, fonteNegrito, 13, "Ingresso confirmado",
                        margem, y, larguraTexto, 18, new java.awt.Color(38, 112, 68));

                float tamanhoQr = 220;
                float xQr = (tamanhoPagina.getWidth() - tamanhoQr) / 2;
                float yQr = y - tamanhoQr - 18;
                PDImageXObject imagemQr = PDImageXObject.createFromByteArray(documento, qrPng, "qr-ingresso");
                conteudo.drawImage(imagemQr, xQr, yQr, tamanhoQr, tamanhoQr);
                y = escreverLinhasPdf(conteudo, fonteNormal, 11,
                        "Apresente este QR Code na entrada do evento.",
                        margem, yQr - 18, larguraTexto, 15, new java.awt.Color(94, 85, 81));
                y -= 12;

                y = escreverCampoPdf(conteudo, fonteNormal, fonteNegrito,
                        "Participante", usuario.getNome(), margem, y, larguraTexto);
                y = escreverCampoPdf(conteudo, fonteNormal, fonteNegrito,
                        "CPF", usuario.getCpf(), margem, y, larguraTexto);
                y = escreverCampoPdf(conteudo, fonteNormal, fonteNegrito,
                        "Data e horário", ingresso.getEvento().getDataHoraFormatada(),
                        margem, y, larguraTexto);
                y = escreverCampoPdf(conteudo, fonteNormal, fonteNegrito,
                        "Local", ingresso.getEvento().getEndereco(), margem, y, larguraTexto);
                escreverLinhasPdf(conteudo, fonteNormal, 9,
                        "Guarde este ingresso no celular. O QR Code contém um identificador único para validação na entrada.",
                        margem, y - 6, larguraTexto, 12, new java.awt.Color(113, 102, 97));
            }

            documento.save(saida);
            return saida.toByteArray();
        }
    }

    private float escreverCampoPdf(PDPageContentStream conteudo, PDType1Font fonteNormal,
                                   PDType1Font fonteNegrito, String rotulo, String valor,
                                   float x, float y, float largura) throws IOException {
        conteudo.setStrokingColor(new java.awt.Color(232, 224, 220));
        conteudo.moveTo(x, y - 5);
        conteudo.lineTo(x + largura, y - 5);
        conteudo.stroke();
        y -= 20;
        y = escreverLinhasPdf(conteudo, fonteNormal, 9, rotulo.toUpperCase(java.util.Locale.ROOT),
                x, y, largura, 12, new java.awt.Color(112, 103, 98));
        return escreverLinhasPdf(conteudo, fonteNegrito, 12, valor,
                x, y - 2, largura, 15, new java.awt.Color(40, 34, 32)) - 8;
    }

    private float escreverLinhasPdf(PDPageContentStream conteudo, PDType1Font fonte, float tamanho,
                                    String texto, float x, float y, float largura, float alturaLinha,
                                    java.awt.Color cor) throws IOException {
        conteudo.setNonStrokingColor(cor);
        conteudo.setFont(fonte, tamanho);
        for (String linha : quebrarTextoPdf(texto, fonte, tamanho, largura)) {
            conteudo.beginText();
            conteudo.newLineAtOffset(x, y);
            conteudo.showText(linha);
            conteudo.endText();
            y -= alturaLinha;
        }
        return y;
    }

    private List<String> quebrarTextoPdf(String texto, PDType1Font fonte, float tamanho, float largura)
            throws IOException {
        List<String> linhas = new ArrayList<>();
        for (String paragrafo : (texto == null ? "" : texto).split("\\R", -1)) {
            StringBuilder linha = new StringBuilder();
            for (String palavra : paragrafo.split("\\s+")) {
                String candidata = linha.isEmpty() ? palavra : linha + " " + palavra;
                if (!linha.isEmpty() && fonte.getStringWidth(candidata) * tamanho / 1000 > largura) {
                    linhas.add(linha.toString());
                    linha = new StringBuilder(palavra);
                } else {
                    linha = new StringBuilder(candidata);
                }
            }
            linhas.add(linha.toString());
        }
        return linhas;
    }

    private String construirUrlValidacaoIngresso(String token) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .pathSegment("ingressos", "validar", token)
                .build()
                .toUriString();
    }

    private byte[] gerarQrPng(String conteudo) throws WriterException, IOException {
        BitMatrix matriz = new QRCodeWriter().encode(
                conteudo, BarcodeFormat.QR_CODE, 320, 320, Map.of(EncodeHintType.MARGIN, 2));
        BufferedImage imagemQr = new BufferedImage(320, 320, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 320; x++) {
            for (int y = 0; y < 320; y++) {
                imagemQr.setRGB(x, y, matriz.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagemQr, "PNG", saida);
        return saida.toByteArray();
    }

    @GetMapping("/ingressos/validar/{token}")
    @Transactional(readOnly = true)
    public String validarIngresso(@PathVariable String token, Model model) {
        Ingresso ingresso = ingressos.findByToken(token).orElse(null);
        if (ingresso == null) {
            return "redirect:/mural";
        }

        model.addAttribute("ingresso", ingresso);
        return "validar-ingresso";
    }

    @GetMapping("/eventos/{id}")
    public String detalhesEvento(@PathVariable Long id, HttpSession session, Model model) {
        Optional<Evento> eventoEncontrado = eventos.findById(id);
        if (eventoEncontrado.isEmpty()) {
            return "redirect:/mural";
        }

        Evento evento = eventoEncontrado.get();
        Usuario produtor = usuarios.findByEmail(evento.getCriadoPorEmail());
        String mapaUrl = UriComponentsBuilder.fromUriString("https://www.google.com/maps/search/")
                .queryParam("api", "1")
                .queryParam("query", evento.getEndereco())
                .build()
                .encode()
                .toUriString();
        model.addAttribute("evento", evento);
        model.addAttribute("produtor", produtor);
        model.addAttribute("mapaUrl", mapaUrl);
        model.addAttribute("usuarioNome", session.getAttribute("usuarioNome"));
        model.addAttribute("usuarioEmail", session.getAttribute("usuarioEmail"));
        return "detalhes-evento";
    }

    @GetMapping("/eventos/novo")
    public String novoEvento(HttpSession session) {
        if (session.getAttribute("usuarioEmail") == null) {
            return "redirect:/login";
        }
        return "novo-evento";
    }

    @PostMapping("/eventos")
    public String criarEvento(
            @RequestParam("nome") String nome,
            @RequestParam("endereco") String endereco,
            @RequestParam("categoria") String categoria,
            @RequestParam("data") String data,
            @RequestParam("hora") String hora,
            @RequestParam(value = "dataFim", required = false) String dataFim,
            @RequestParam(value = "descricao", required = false) String descricao,
            @RequestParam(value = "limiteParticipantes", required = false) Integer limiteParticipantes,
            @RequestParam(value = "pago", defaultValue = "false") boolean pago,
            @RequestParam(value = "valorIngresso", required = false) String valorIngresso,
            @RequestParam(value = "informacaoOrganizador", required = false) String informacaoOrganizador,
            @RequestParam(value = "capa", required = false) MultipartFile capa,
            @RequestParam(value = "imagens", required = false) MultipartFile[] arquivos,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Object email = session.getAttribute("usuarioEmail");
        if (email == null) {
            return "redirect:/login";
        }

        if (limiteParticipantes != null && limiteParticipantes < 1) {
            redirectAttributes.addFlashAttribute("erroEvento", "O limite de participantes deve ser maior que zero.");
            return "redirect:/eventos/novo";
        }

        BigDecimal valor;
        try {
            valor = parseValorIngresso(pago, valorIngresso);
        } catch (IllegalArgumentException erro) {
            redirectAttributes.addFlashAttribute("erroEvento", erro.getMessage());
            return "redirect:/eventos/novo";
        }

        LocalDateTime dataHora;
        LocalDate dataFinal;
        try {
            LocalDate dataInicial = LocalDate.parse(data.trim());
            dataHora = dataInicial.atTime(LocalTime.parse(hora.trim()));
            dataFinal = parseDataFinal(dataFim);
            validarPeriodo(dataInicial, dataFinal);
        } catch (DateTimeParseException erro) {
            redirectAttributes.addFlashAttribute("erroEvento", "Selecione datas e horário válidos para o evento.");
            return "redirect:/eventos/novo";
        } catch (IllegalArgumentException erro) {
            redirectAttributes.addFlashAttribute("erroEvento", erro.getMessage());
            return "redirect:/eventos/novo";
        }

        ImagemEventoService.ImagensEvento fotos;
        try {
            fotos = imagens.salvarEvento(capa, arquivos, true);
        } catch (IllegalArgumentException | IOException erro) {
            redirectAttributes.addFlashAttribute("erroEvento", erro.getMessage());
            return "redirect:/eventos/novo";
        }

        Evento evento = new Evento(nome, endereco, categoria, dataHora, descricao, email.toString(),
                limiteParticipantes, dataFinal, pago, valor);
        evento.definirImagemCapa(fotos.capa());
        fotos.galeria().forEach(evento::adicionarImagem);
        evento.definirInformacaoOrganizador(informacaoOrganizador);
        eventos.save(evento);
        return "redirect:/mural";
    }

    @GetMapping("/eventos/{id}/editar")
    public String paginaEdicaoEvento(@PathVariable Long id, HttpSession session, Model model,
                                     RedirectAttributes mensagens) {
        Object email = session.getAttribute("usuarioEmail");
        if (email == null) {
            return "redirect:/login";
        }

        Optional<Evento> eventoEncontrado = eventos.findById(id);
        if (eventoEncontrado.isEmpty() || !eventoEncontrado.get().foiCriadoPor(email.toString())) {
            mensagens.addFlashAttribute("erroMural", "Você só pode editar os eventos que criou.");
            return "redirect:/mural";
        }

        model.addAttribute("evento", eventoEncontrado.get());
        return "editar-evento";
    }

    @PostMapping("/eventos/{id}/editar")
    public String atualizarEvento(
            @PathVariable Long id,
            @RequestParam("nome") String nome,
            @RequestParam("endereco") String endereco,
            @RequestParam("categoria") String categoria,
            @RequestParam("data") String data,
            @RequestParam("hora") String hora,
            @RequestParam(value = "dataFim", required = false) String dataFim,
            @RequestParam(value = "descricao", required = false) String descricao,
            @RequestParam(value = "limiteParticipantes", required = false) Integer limiteParticipantes,
            @RequestParam(value = "pago", defaultValue = "false") boolean pago,
            @RequestParam(value = "valorIngresso", required = false) String valorIngresso,
            @RequestParam(value = "informacaoOrganizador", required = false) String informacaoOrganizador,
            @RequestParam(value = "capa", required = false) MultipartFile capa,
            @RequestParam(value = "imagens", required = false) MultipartFile[] arquivos,
            @RequestParam(value = "imagensRemovidas", required = false) List<String> imagensRemovidas,
            HttpSession session,
            RedirectAttributes mensagens) {
        Object email = session.getAttribute("usuarioEmail");
        if (email == null) {
            return "redirect:/login";
        }

        Optional<Evento> eventoEncontrado = eventos.findById(id);
        if (eventoEncontrado.isEmpty() || !eventoEncontrado.get().foiCriadoPor(email.toString())) {
            mensagens.addFlashAttribute("erroMural", "Você só pode editar os eventos que criou.");
            return "redirect:/mural";
        }

        if (limiteParticipantes != null && limiteParticipantes < 1) {
            mensagens.addFlashAttribute("erroEvento", "O limite de participantes deve ser maior que zero.");
            return "redirect:/eventos/" + id + "/editar";
        }

        Evento evento = eventoEncontrado.get();
        if (limiteParticipantes != null && limiteParticipantes < evento.getQuantidadeParticipantes()) {
            mensagens.addFlashAttribute("erroEvento",
                    "O limite não pode ser menor que as " + evento.getQuantidadeParticipantes() + " pessoas já confirmadas.");
            return "redirect:/eventos/" + id + "/editar";
        }

        BigDecimal valor;
        try {
            valor = parseValorIngresso(pago, valorIngresso);
        } catch (IllegalArgumentException erro) {
            mensagens.addFlashAttribute("erroEvento", erro.getMessage());
            return "redirect:/eventos/" + id + "/editar";
        }

        LocalDateTime dataHora;
        LocalDate dataFinal;
        try {
            LocalDate dataInicial = LocalDate.parse(data.trim());
            dataHora = dataInicial.atTime(LocalTime.parse(hora.trim()));
            dataFinal = parseDataFinal(dataFim);
            validarPeriodo(dataInicial, dataFinal);
        } catch (DateTimeParseException erro) {
            mensagens.addFlashAttribute("erroEvento", "Selecione datas e horário válidos para o evento.");
            return "redirect:/eventos/" + id + "/editar";
        } catch (IllegalArgumentException erro) {
            mensagens.addFlashAttribute("erroEvento", erro.getMessage());
            return "redirect:/eventos/" + id + "/editar";
        }

        List<String> caminhosAntigos = evento.getTodosCaminhosImagens();
        Set<String> remover = imagensRemovidas == null ? new HashSet<>() : new HashSet<>(imagensRemovidas);
        remover.retainAll(caminhosAntigos);
        String capaAtual = evento.getImagemCapa();
        boolean capaRemovida = capaAtual != null && remover.contains(capaAtual);
        if (capaRemovida) {
            capaAtual = null;
        }
        List<String> galeriaAtual = new ArrayList<>(evento.getImagensGaleria());
        galeriaAtual.removeIf(remover::contains);
        if (capaAtual == null && !galeriaAtual.isEmpty()) {
            capaAtual = galeriaAtual.remove(0);
        }
        try {
            ImagemEventoService.ImagensEvento novasFotos = imagens.salvarEvento(
                    capa, arquivos, capaAtual == null && !capaRemovida);
            String capaAtualizada = novasFotos.capa() != null ? novasFotos.capa() : capaAtual;
            List<String> galeriaAtualizada = !novasFotos.galeria().isEmpty()
                    ? novasFotos.galeria()
                    : galeriaAtual;
            evento.definirImagemCapa(capaAtualizada);
            evento.substituirImagens(galeriaAtualizada);
        } catch (IllegalArgumentException | IOException erro) {
            mensagens.addFlashAttribute("erroEvento", erro.getMessage());
            return "redirect:/eventos/" + id + "/editar";
        }

        evento.atualizar(nome, endereco, categoria, dataHora, dataFinal, descricao,
                limiteParticipantes, pago, valor, informacaoOrganizador);
        eventos.save(evento);
        List<String> caminhosNovos = evento.getTodosCaminhosImagens();
        caminhosAntigos.removeAll(caminhosNovos);
        try {
            imagens.remover(caminhosAntigos);
        } catch (IOException erro) {
            mensagens.addFlashAttribute("erroMural",
                    "O evento foi atualizado, mas não foi possível remover todas as imagens substituídas.");
        }
        mensagens.addFlashAttribute("mensagemMural", "Evento atualizado com sucesso.");
        return "redirect:/mural";
    }

    private LocalDate parseDataFinal(String dataFim) {
        return dataFim == null || dataFim.isBlank() ? null : LocalDate.parse(dataFim.trim());
    }

    private void validarPeriodo(LocalDate dataInicial, LocalDate dataFinal) {
        if (dataFinal != null && dataFinal.isBefore(dataInicial)) {
            throw new IllegalArgumentException("A data final não pode ser anterior à data inicial.");
        }
    }

    private BigDecimal parseValorIngresso(boolean pago, String valorIngresso) {
        if (!pago) {
            return null;
        }
        if (valorIngresso == null || valorIngresso.isBlank()) {
            throw new IllegalArgumentException("Informe o valor do ingresso para um evento pago.");
        }
        try {
            BigDecimal valor = new BigDecimal(valorIngresso.trim());
            if (valor.signum() <= 0 || valor.scale() > 2) {
                throw new IllegalArgumentException("Informe um valor maior que zero, com até duas casas decimais.");
            }
            return valor;
        } catch (NumberFormatException erro) {
            throw new IllegalArgumentException("Informe um valor válido para o ingresso.");
        }
    }

    @PostMapping("/eventos/{id}/excluir")
    public String excluirEvento(@PathVariable Long id, HttpSession session, RedirectAttributes mensagens) {
        Object email = session.getAttribute("usuarioEmail");
        if (email == null) {
            return "redirect:/login";
        }

        Optional<Evento> eventoEncontrado = eventos.findById(id);
        if (eventoEncontrado.isEmpty() || !eventoEncontrado.get().foiCriadoPor(email.toString())) {
            mensagens.addFlashAttribute("erroMural", "Você só pode excluir os eventos que criou.");
            return "redirect:/mural";
        }

        Evento evento = eventoEncontrado.get();
        List<String> caminhosImagens = List.copyOf(evento.getTodosCaminhosImagens());
        ingressos.deleteByEvento_Id(evento.getId());
        eventos.delete(evento);

        try {
            imagens.remover(caminhosImagens);
        } catch (IOException erro) {
            mensagens.addFlashAttribute("erroMural",
                    "O evento foi excluído, mas não foi possível remover todos os arquivos de imagem.");
            return "redirect:/mural";
        }

        mensagens.addFlashAttribute("mensagemMural", "Evento excluído com sucesso.");
        return "redirect:/mural";
    }

    @PostMapping("/participar/{id}")
    @Transactional
    public String participarEvento(@PathVariable Long id, HttpSession session, RedirectAttributes mensagens) {
        Usuario usuarioSessao = (Usuario) session.getAttribute("usuarioLogado");
        if (usuarioSessao == null) {
            return "redirect:/login";
        }

        Usuario usuario = usuarios.findById(usuarioSessao.getId()).orElse(null);
        if (usuario == null) {
            session.invalidate();
            return "redirect:/login";
        }

        Evento evento = eventos.findByIdForUpdate(id).orElse(null);
        if (evento == null) {
            return "redirect:/mural";
        }

        boolean jaParticipa = evento.isParticipante(usuario);
        if (jaParticipa) {
            evento.getParticipantes().removeIf(u -> u.getId().equals(usuario.getId()));
            ingressos.deleteByEvento_IdAndUsuario_Id(evento.getId(), usuario.getId());
            mensagens.addFlashAttribute("mensagemMural", "Sua presença foi cancelada.");
            eventos.save(evento);
            return "redirect:/mural";
        }

        if (evento.isLotacaoEsgotada()) {
            mensagens.addFlashAttribute("erroMural", "Este evento está lotado.");
            return "redirect:/mural";
        }

        evento.getParticipantes().add(usuario);
        ingressos.save(new Ingresso(evento, usuario));
        mensagens.addFlashAttribute("sucessoInscricao", true);
        mensagens.addFlashAttribute("mensagemMural",
                "Sucesso! Sua vaga foi confirmada. Agradecemos pela sua inscrição! Acesse mais detalhes dos seus eventos confirmados na aba Meus eventos.");
        eventos.save(evento);
        return "redirect:/mural";
    }

    @GetMapping("/uploads/eventos/{nomeArquivo:.+}")
    public ResponseEntity<Resource> imagem(@PathVariable String nomeArquivo) throws IOException {
        Resource recurso = imagens.carregar(nomeArquivo);
        if (recurso == null) {
            return ResponseEntity.notFound().build();
        }

        MediaType tipo = MediaTypeFactory.getMediaType(recurso)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(tipo).body(recurso);
    }
} 