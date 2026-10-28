package com.example.demo;

import jakarta.servlet.http.HttpSession;

import org.springframework.core.io.Resource;
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

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Controller
public class EventoController {

    private final EventoRepository eventos;
    private final ImagemEventoService imagens;

    public UsuarioRepository usuarios;

    public EventoController(EventoRepository eventos, ImagemEventoService imagens, UsuarioRepository usuarios) {
        this.eventos = eventos;
        this.imagens = imagens;   
        this.usuarios = usuarios;
    }

    @GetMapping("/")
    public String inicio() {
        return "inicio";
    }

    @GetMapping("/mural")
    public String mural(HttpSession session, Model model) {
        model.addAttribute("eventos", eventos.findAllByOrderByDataHoraAsc());
        model.addAttribute("usuarioNome", session.getAttribute("usuarioNome"));
        model.addAttribute("usuarioEmail", session.getAttribute("usuarioEmail"));
        return "index";
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
            @RequestParam(value = "descricao", required = false) String descricao,
            @RequestParam(value = "imagens", required = false) MultipartFile[] arquivos,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        Object email = session.getAttribute("usuarioEmail");
        if (email == null) {
            return "redirect:/login";
        }

        LocalDateTime dataHora;
        try {
            dataHora = LocalDate.parse(data.trim()).atTime(LocalTime.parse(hora.trim()));
        } catch (DateTimeParseException erro) {
            redirectAttributes.addFlashAttribute("erroEvento", "Selecione uma data e um horário válidos.");
            return "redirect:/eventos/novo";
        }

        List<String> caminhos;
        try {
            caminhos = imagens.salvar(arquivos);
        } catch (IllegalArgumentException | IOException erro) {
            redirectAttributes.addFlashAttribute("erroEvento", erro.getMessage());
            return "redirect:/eventos/novo";
        }

        Evento evento = new Evento(nome, endereco, categoria, dataHora, descricao, email.toString());
        caminhos.forEach(evento::adicionarImagem);
        eventos.save(evento);
        return "redirect:/mural";
    }

   @PostMapping("/participar/{id}")
    public String participarEvento(@PathVariable Long id, HttpSession session) {
    // 1. Obtém o usuário armazenado na sessão
    Usuario usuarioSessao = (Usuario) session.getAttribute("usuarioLogado");
    if (usuarioSessao == null) {
        return "redirect:/login";
    }

    // 2. Busca o usuário gerenciado pelo JPA na base de dados
    Usuario usuario = usuarios.findById(usuarioSessao.getId()).orElse(null);
    if (usuario == null) {
        session.invalidate();
        return "redirect:/login";
    }

    // 3. Procura o evento e atualiza a lista de participantes
    Evento evento = eventos.findById(id).orElse(null);
    if (evento != null) {
        boolean jaParticipa = evento.getParticipantes().stream()
                .anyMatch(u -> u.getId().equals(usuario.getId()));

        if (jaParticipa) {
            evento.getParticipantes().removeIf(u -> u.getId().equals(usuario.getId()));
        } else {
            evento.getParticipantes().add(usuario);
        }
        eventos.save(evento);
    }

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