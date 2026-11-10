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
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Controller
public class EventoController {

    private final EventoRepository eventos;
    private final ImagemEventoService imagens;

    private final UsuarioRepository usuarios;

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
            @RequestParam(value = "limiteParticipantes", required = false) Integer limiteParticipantes,
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

        Evento evento = new Evento(nome, endereco, categoria, dataHora, descricao, email.toString(), limiteParticipantes);
        caminhos.forEach(evento::adicionarImagem);
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
            @RequestParam(value = "descricao", required = false) String descricao,
            @RequestParam(value = "limiteParticipantes", required = false) Integer limiteParticipantes,
            @RequestParam(value = "imagens", required = false) MultipartFile[] arquivos,
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

        LocalDateTime dataHora;
        try {
            dataHora = LocalDate.parse(data.trim()).atTime(LocalTime.parse(hora.trim()));
        } catch (DateTimeParseException erro) {
            mensagens.addFlashAttribute("erroEvento", "Selecione uma data e um horário válidos.");
            return "redirect:/eventos/" + id + "/editar";
        }

        try {
            List<String> novosCaminhos = imagens.salvar(arquivos);
            if (!novosCaminhos.isEmpty()) {
                evento.substituirImagens(novosCaminhos);
            }
        } catch (IllegalArgumentException | IOException erro) {
            mensagens.addFlashAttribute("erroEvento", erro.getMessage());
            return "redirect:/eventos/" + id + "/editar";
        }

        evento.atualizar(nome, endereco, categoria, dataHora, descricao, limiteParticipantes);
        eventos.save(evento);
        mensagens.addFlashAttribute("mensagemMural", "Evento atualizado com sucesso.");
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
            mensagens.addFlashAttribute("mensagemMural", "Sua presença foi cancelada.");
            eventos.save(evento);
            return "redirect:/mural";
        }

        if (evento.isLotacaoEsgotada()) {
            mensagens.addFlashAttribute("erroMural", "Este evento está lotado.");
            return "redirect:/mural";
        }

        evento.getParticipantes().add(usuario);
        mensagens.addFlashAttribute("mensagemMural", "Sua presença foi confirmada.");
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