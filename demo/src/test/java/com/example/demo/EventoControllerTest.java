package com.example.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventoControllerTest {

    private EventoRepository eventos;
    private ImagemEventoService imagens;
    private UsuarioRepository usuarios;
    private EventoController controller;

    @BeforeEach
    void setUp() {
        eventos = mock(EventoRepository.class);
        imagens = mock(ImagemEventoService.class);
        usuarios = mock(UsuarioRepository.class);
        controller = new EventoController(eventos, imagens, usuarios);
    }

    @Test
    void somenteCriadorPodeAbrirEdicao() {
        Evento evento = criarEvento();
        when(eventos.findById(7L)).thenReturn(Optional.of(evento));

        MockHttpSession sessaoCriador = new MockHttpSession();
        sessaoCriador.setAttribute("usuarioEmail", "criador@example.com");
        String pagina = controller.paginaEdicaoEvento(
                7L, sessaoCriador, new ExtendedModelMap(), new RedirectAttributesModelMap());

        MockHttpSession sessaoOutraPessoa = new MockHttpSession();
        sessaoOutraPessoa.setAttribute("usuarioEmail", "outra@example.com");
        String redirecionamento = controller.paginaEdicaoEvento(
                7L, sessaoOutraPessoa, new ExtendedModelMap(), new RedirectAttributesModelMap());

        assertEquals("editar-evento", pagina);
        assertEquals("redirect:/mural", redirecionamento);
    }

    @Test
    void outraPessoaNaoPodeAlterarEventoNemSalvarImagens() throws Exception {
        Evento evento = criarEvento();
        when(eventos.findById(7L)).thenReturn(Optional.of(evento));
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioEmail", "outra@example.com");

        String resultado = controller.atualizarEvento(
                7L, "Nome alterado", "Outro local", "Cultura", "2026-12-01", "20:30",
                "Descrição alterada", null, null, sessao, new RedirectAttributesModelMap());

        assertEquals("redirect:/mural", resultado);
        assertEquals("Evento original", evento.getNome());
        verify(imagens, never()).salvar(any());
        verify(eventos, never()).save(any(Evento.class));
    }

    @Test
    void criadorPodeAtualizarDetalhesEImagens() throws Exception {
        Evento evento = criarEvento();
        evento.adicionarImagem("/uploads/eventos/antiga.png");
        when(eventos.findById(7L)).thenReturn(Optional.of(evento));
        when(imagens.salvar(any())).thenReturn(List.of("/uploads/eventos/nova.png"));
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioEmail", "CRIADOR@example.com");

        String resultado = controller.atualizarEvento(
                7L, "Novo nome", "Novo local", "Cultura", "2026-12-01", "20:30",
                "Nova descrição", 25, null, sessao, new RedirectAttributesModelMap());

        assertEquals("redirect:/mural", resultado);
        assertEquals("Novo nome", evento.getNome());
        assertEquals("Novo local", evento.getEndereco());
        assertEquals("Cultura", evento.getCategoria());
        assertEquals(LocalDateTime.of(2026, 12, 1, 20, 30), evento.getDataHora());
        assertEquals("Nova descrição", evento.getDescricao());
        assertEquals(25, evento.getLimiteParticipantes());
        assertEquals(List.of("/uploads/eventos/nova.png"), evento.getImagens());
        verify(eventos).save(evento);
    }

    @Test
    void naoPermiteDefinirLimiteAbaixoDasPresencasConfirmadas() throws Exception {
        Evento evento = criarEventoComLimite(5);
        for (long id : List.of(1L, 2L, 3L)) {
            Usuario inscrito = mock(Usuario.class);
            when(inscrito.getId()).thenReturn(id);
            evento.getParticipantes().add(inscrito);
        }
        when(eventos.findById(7L)).thenReturn(Optional.of(evento));
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioEmail", "criador@example.com");

        String resultado = controller.atualizarEvento(
                7L, "Novo nome", "Novo local", "Cultura", "2026-12-01", "20:30",
                "Descrição", 2, null, sessao, new RedirectAttributesModelMap());

        assertEquals("redirect:/eventos/7/editar", resultado);
        assertEquals(5, evento.getLimiteParticipantes());
        assertEquals("Evento com limite", evento.getNome());
        verify(imagens, never()).salvar(any());
        verify(eventos, never()).save(any(Evento.class));
    }

    @Test
    void naoPermiteInscricaoQuandoEventoEstaLotado() {
        Evento evento = criarEventoComLimite(1);
        Usuario inscrito = mock(Usuario.class);
        when(inscrito.getId()).thenReturn(99L);
        evento.getParticipantes().add(inscrito);
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(10L);
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario));
        when(eventos.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));

        String resultado = controller.participarEvento(7L, sessaoComUsuario(10L),
                new RedirectAttributesModelMap());

        assertEquals("redirect:/mural", resultado);
        assertEquals(1, evento.getQuantidadeParticipantes());
        verify(eventos, never()).save(any(Evento.class));
    }

    @Test
    void permiteCancelarPresencaMesmoQuandoEventoEstaLotado() {
        Evento evento = criarEventoComLimite(1);
        Usuario usuarioInscrito = mock(Usuario.class);
        when(usuarioInscrito.getId()).thenReturn(10L);
        evento.getParticipantes().add(usuarioInscrito);

        Usuario usuarioGerenciado = mock(Usuario.class);
        when(usuarioGerenciado.getId()).thenReturn(10L);
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuarioGerenciado));
        when(eventos.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));

        controller.participarEvento(7L, sessaoComUsuario(10L), new RedirectAttributesModelMap());

        assertEquals(0, evento.getQuantidadeParticipantes());
        verify(eventos).save(evento);
    }

    @Test
    void permiteInscricaoEnquantoHouverVaga() {
        Evento evento = criarEventoComLimite(2);
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(10L);
        when(usuarios.findById(10L)).thenReturn(Optional.of(usuario));
        when(eventos.findByIdForUpdate(7L)).thenReturn(Optional.of(evento));

        controller.participarEvento(7L, sessaoComUsuario(10L), new RedirectAttributesModelMap());

        assertEquals(1, evento.getQuantidadeParticipantes());
        assertEquals(usuario, evento.getParticipantes().get(0));
        verify(eventos).save(evento);
    }

    @Test
    void mostraVagasRestantesQuandoFaltamTrintaOuMenos() {
        Evento evento = criarEventoComLimite(200);
        for (long id = 1; id <= 170; id++) {
            Usuario inscrito = mock(Usuario.class);
            when(inscrito.getId()).thenReturn(id);
            evento.getParticipantes().add(inscrito);
        }

        assertEquals(true, evento.deveMostrarVagasRestantes());
        assertEquals("Restam 30 vagas.", evento.getMensagemVagasRestantes());
    }

    @Test
    void naoMostraAvisoDeVagasRestantesQuandoHaMaisDeTrinta() {
        Evento evento = criarEventoComLimite(200);
        for (long id = 1; id <= 169; id++) {
            Usuario inscrito = mock(Usuario.class);
            when(inscrito.getId()).thenReturn(id);
            evento.getParticipantes().add(inscrito);
        }

        assertEquals(false, evento.deveMostrarVagasRestantes());
        assertEquals("", evento.getMensagemVagasRestantes());
    }

    private Evento criarEvento() {
        return new Evento(
                "Evento original",
                "Local original",
                "Geral",
                LocalDateTime.of(2026, 12, 1, 19, 0),
                "Descrição original",
                "criador@example.com");
    }

    private Evento criarEventoComLimite(int limite) {
        Evento evento = new Evento(
                "Evento com limite",
                "Local original",
                "Geral",
                LocalDateTime.of(2026, 12, 1, 19, 0),
                "Descrição original",
                "criador@example.com",
                limite);
        return evento;
    }

    private MockHttpSession sessaoComUsuario(Long id) {
        Usuario usuarioSessao = mock(Usuario.class);
        when(usuarioSessao.getId()).thenReturn(id);
        MockHttpSession sessao = new MockHttpSession();
        sessao.setAttribute("usuarioLogado", usuarioSessao);
        return sessao;
    }
}
