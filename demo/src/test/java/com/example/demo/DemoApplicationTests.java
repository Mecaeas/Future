package com.example.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DemoApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EventoRepository eventos;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private IngressoRepository ingressos;

	@Autowired
	private ImagemEventoService imagens;

	private final java.util.List<String> arquivosEnviados = new java.util.ArrayList<>();

	@AfterEach
	void limparImagensDeTeste() throws IOException {
		imagens.remover(arquivosEnviados);
		arquivosEnviados.clear();
	}

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void muralExibeVagasDoEvento() throws Exception {
		eventos.save(new Evento(
				"Evento de teste",
				"Local de teste",
				"Geral",
				LocalDateTime.of(2026, 12, 1, 19, 0),
				"Descrição de teste",
				"criador@example.com",
				5,
				LocalDate.of(2026, 12, 3)));

		mockMvc.perform(get("/mural"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Ingressos disponíveis:")))
				.andExpect(content().string(containsString("Limite de pessoas:")))
				.andExpect(content().string(containsString("01/12/2026 até 03/12/2026 às 19:00")))
				.andExpect(content().string(containsString("5</strong>")));
	}

	@Test
	void capaNaoApareceNovamenteNaGaleriaMesmoSeEstiverNaListaLegada() {
		Evento evento = new Evento(
				"Evento com imagens legadas",
				"Local de teste",
				"Cultura",
				LocalDateTime.of(2026, 12, 1, 19, 0),
				"Descrição de teste",
				"criador@example.com");
		evento.definirImagemCapa("/uploads/eventos/capa.png");
		evento.adicionarImagem("/uploads/eventos/capa.png");
		evento.adicionarImagem("/uploads/eventos/foto-1.png");

		assertEquals(List.of("/uploads/eventos/foto-1.png"), evento.getImagensGaleria());
		assertEquals(List.of("/uploads/eventos/capa.png", "/uploads/eventos/foto-1.png"),
				evento.getTodosCaminhosImagens());
	}

	@Test
	void detalhesDoEventoMostramProdutorEnderecoMapaEIngresso() throws Exception {
		Usuario produtor = usuarios.saveAndFlush(new Cidadao(
				"Produtor de teste", "produtor-detalhes@example.com", "senha", LocalDate.of(1990, 1, 1)));
		Usuario participante = usuarios.saveAndFlush(new Cidadao(
				"Participante de teste", "participante-detalhes@example.com", "senha", LocalDate.of(1990, 1, 1)));
		Evento evento = eventos.saveAndFlush(new Evento(
				"Evento detalhado",
				"Rua das Flores, 123, Pouso Alegre - MG",
				"Cultura",
				LocalDateTime.of(2026, 12, 1, 19, 0),
				"Descrição completa com informações importantes.",
				produtor.getEmail(),
				40,
				null,
				true,
				new BigDecimal("25.90")));
		evento.definirInformacaoOrganizador("Produção acadêmica local.");
		eventos.saveAndFlush(evento);
		MockHttpSession sessao = new MockHttpSession();
		sessao.setAttribute("usuarioLogado", participante);

		mockMvc.perform(get("/eventos/{id}", evento.getId()).session(sessao))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Descrição completa com informações importantes.")))
				.andExpect(content().string(containsString("Rua das Flores, 123, Pouso Alegre - MG")))
				.andExpect(content().string(containsString("Abrir endereço no Google Maps")))
				.andExpect(content().string(containsString("Produtor de teste")))
				.andExpect(content().string(containsString("produtor-detalhes@example.com")))
				.andExpect(content().string(containsString("25,90")))
				.andExpect(content().string(containsString("Comprar ingresso (simulação)")))
				.andExpect(content().string(containsString("nenhum pagamento real será cobrado")))
				.andExpect(content().string(containsString("Produção acadêmica local.")));
	}

	@Test
	@Transactional
	void criarEventoPermiteDataFinalOpcional() throws Exception {
		MockHttpSession sessao = new MockHttpSession();
		sessao.setAttribute("usuarioEmail", "criador-periodo@example.com");

		mockMvc.perform(multipart("/eventos")
						.file(imagemValida("capa", "capa.png"))
						.file(imagemValida("imagens", "galeria.png"))
						.file(imagemValida("imagens", "galeria-2.png"))
						.session(sessao)
						.param("nome", "Evento de vários dias")
						.param("endereco", "Local de teste")
						.param("categoria", "Cultura")
						.param("data", "2026-12-10")
						.param("hora", "18:30")
						.param("dataFim", "2026-12-12")
						.param("informacaoOrganizador", "Organizado pela comunidade local."))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mural"));

		Evento evento = eventos.findAll().stream()
				.filter(item -> item.getNome().equals("Evento de vários dias"))
				.findFirst()
				.orElseThrow();
		assertEquals(LocalDate.of(2026, 12, 12), evento.getDataFim());
		assertEquals("10/12/2026 às 18:30 até 12/12/2026", evento.getDataHoraFormatada());
		assertEquals("Organizado pela comunidade local.", evento.getInformacaoOrganizador());
		assertNotNull(evento.getImagemCapa());
		assertEquals(2, evento.getImagensGaleria().size());
		arquivosEnviados.addAll(evento.getTodosCaminhosImagens());

		mockMvc.perform(get("/eventos/{id}", evento.getId()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Foto de capa do evento Evento de vários dias")))
				.andExpect(content().string(containsString("Fotos do evento")))
				.andExpect(content().string(containsString("Imagem 1 do evento Evento de vários dias")))
				.andExpect(content().string(containsString("Imagem 2 do evento Evento de vários dias")))
				.andExpect(content().string(containsString("aria-label=\"Próxima foto\"")))
				.andExpect(content().string(containsString("1 de 2")))
				.andExpect(content().string(containsString("Organizado pela comunidade local.")));

		MockHttpSession sessaoEdicao = new MockHttpSession();
		sessaoEdicao.setAttribute("usuarioEmail", "criador-periodo@example.com");
		mockMvc.perform(get("/eventos/{id}/editar", evento.getId()).session(sessaoEdicao))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Fotos do evento")))
				.andExpect(content().string(containsString("Excluir foto de capa do evento")))
				.andExpect(content().string(containsString("imagensRemovidas")))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						containsString("Adicionar fotos à galeria"))));
	}

	@Test
	@Transactional
	void criarEventoContinuaAceitandoDataFinalVazia() throws Exception {
		MockHttpSession sessao = new MockHttpSession();
		sessao.setAttribute("usuarioEmail", "criador-sem-fim@example.com");

		mockMvc.perform(multipart("/eventos")
						.file(imagemValida("capa", "capa.png"))
						.session(sessao)
						.param("nome", "Evento de um dia")
						.param("endereco", "Local de teste")
						.param("categoria", "Cultura")
						.param("data", "2026-12-10")
						.param("hora", "18:30")
						.param("dataFim", ""))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mural"));

		Evento evento = eventos.findAll().stream()
				.filter(item -> item.getNome().equals("Evento de um dia"))
				.findFirst()
				.orElseThrow();
		assertNull(evento.getDataFim());
		assertEquals("10/12/2026 às 18:30", evento.getDataHoraFormatada());
		arquivosEnviados.addAll(evento.getTodosCaminhosImagens());
	}

	@Test
	@Transactional
	void criarEventoRejeitaDataFinalAnteriorADataInicial() throws Exception {
		MockHttpSession sessao = new MockHttpSession();
		sessao.setAttribute("usuarioEmail", "criador-periodo-invalido@example.com");

		mockMvc.perform(multipart("/eventos")
						.file(imagemValida("capa", "capa.png"))
						.session(sessao)
						.param("nome", "Evento com período inválido")
						.param("endereco", "Local de teste")
						.param("categoria", "Cultura")
						.param("data", "2026-12-10")
						.param("hora", "18:30")
						.param("dataFim", "2026-12-09"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/eventos/novo"));

		assertFalse(eventos.findAll().stream()
				.anyMatch(item -> item.getNome().equals("Evento com período inválido")));
	}

	@Test
	@Transactional
	void criarEventoPagoExigeValorERegistraPreco() throws Exception {
		MockHttpSession sessao = new MockHttpSession();
		sessao.setAttribute("usuarioEmail", "criador-pago@example.com");

		mockMvc.perform(multipart("/eventos")
						.file(imagemValida("capa", "capa.png"))
						.session(sessao)
						.param("nome", "Evento pago de teste")
						.param("endereco", "Local de teste")
						.param("categoria", "Cultura")
						.param("data", "2026-12-10")
						.param("hora", "18:30")
						.param("pago", "true")
						.param("valorIngresso", "49.90"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mural"));

		Evento evento = eventos.findAll().stream()
				.filter(item -> item.getNome().equals("Evento pago de teste"))
				.findFirst()
				.orElseThrow();
		assertTrue(evento.isPago());
		assertEquals(new BigDecimal("49.90"), evento.getValorIngresso());
		arquivosEnviados.addAll(evento.getTodosCaminhosImagens());
	}

	@Test
	void criarEventoRejeitaAusenciaDeFotoDeCapa() throws Exception {
		MockHttpSession sessao = new MockHttpSession();
		sessao.setAttribute("usuarioEmail", "criador-sem-capa@example.com");

		mockMvc.perform(multipart("/eventos")
						.session(sessao)
						.param("nome", "Evento sem capa")
						.param("endereco", "Local de teste")
						.param("categoria", "Cultura")
						.param("data", "2026-12-10")
						.param("hora", "18:30"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/eventos/novo"));

		assertFalse(eventos.findAll().stream().anyMatch(evento -> evento.getNome().equals("Evento sem capa")));
	}

	@Test
	void sacExibeContatosEFormularioDeFeedback() throws Exception {
		mockMvc.perform(get("/sac"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("micaiasvinagre008@gmail.com")))
				.andExpect(content().string(containsString("(35) 97400-6154")))
				.andExpect(content().string(containsString("Enviar feedback")));
	}

	@Test
	void muralExibeAcoesDeGerenciamentoParaOCriador() throws Exception {
		Evento evento = eventos.saveAndFlush(new Evento(
				"Evento do criador",
				"Local de teste",
				"Educação",
				LocalDateTime.of(2026, 12, 3, 19, 0),
				"Descrição de teste",
				"criador-mural@example.com"));
		MockHttpSession sessao = new MockHttpSession();
		sessao.setAttribute("usuarioEmail", "criador-mural@example.com");

		mockMvc.perform(get("/mural").session(sessao))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("event-media")))
				.andExpect(content().string(containsString("Mais detalhes")))
				.andExpect(content().string(containsString("Editar evento")))
				.andExpect(content().string(containsString("Excluir evento")))
				.andExpect(content().string(containsString("Educação")))
				.andExpect(content().string(containsString("/eventos/" + evento.getId() + "/excluir")))
				.andExpect(content().string(containsString("Atendimento MWK Eventos")))
				.andExpect(content().string(containsString("href=\"/sac#duvidas\"")))
				.andExpect(content().string(containsString("href=\"/sac#feedback\"")));
	}

	@Test
	void criadorConsegueExcluirEventoPeloMural() throws Exception {
		Evento evento = eventos.saveAndFlush(new Evento(
				"Evento para excluir",
				"Local de teste",
				"Geral",
				LocalDateTime.of(2026, 12, 4, 19, 0),
				"Descrição de teste",
				"criador-exclusao@example.com"));
		MockHttpSession sessao = new MockHttpSession();
		sessao.setAttribute("usuarioEmail", "criador-exclusao@example.com");

		mockMvc.perform(post("/eventos/{id}/excluir", evento.getId()).session(sessao))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mural"));

		assertFalse(eventos.existsById(evento.getId()));
	}

	@Test
	@Transactional
	void cadastroDeEmpresaNormalizaCnpjComPontuacao() throws Exception {
		mockMvc.perform(post("/fazer-registo")
						.param("nome", "Empresa de teste")
						.param("email", "empresa-cnpj-teste@example.com")
						.param("senha", "senha-segura-123")
						.param("tipo", "PJ")
						.param("cpf", "529.982.247-25")
						.param("documento", "12.345.678/0001-99"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));

		PessoaJuridica empresa = (PessoaJuridica) usuarios.findByEmail("empresa-cnpj-teste@example.com");
		assertEquals("12.345.678/0001-99", empresa.getCnpj());
		assertEquals("529.982.247-25", empresa.getCpf());

		mockMvc.perform(post("/fazer-registo")
						.param("nome", "Empresa sem máscara")
						.param("email", "empresa-cnpj-digitos@example.com")
						.param("senha", "senha-segura-123")
						.param("tipo", "PJ")
						.param("cpf", "529.982.247-25")
						.param("documento", "12345678000199"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));

		PessoaJuridica empresaSemMascara = (PessoaJuridica) usuarios.findByEmail("empresa-cnpj-digitos@example.com");
		assertEquals("12.345.678/0001-99", empresaSemMascara.getCnpj());
	}

	@Test
	@Transactional
	void cadastroDeEmpresaRejeitaCnpjComQuantidadeIncorretaDeDigitos() throws Exception {
		mockMvc.perform(post("/fazer-registo")
						.param("nome", "Empresa inválida")
						.param("email", "empresa-cnpj-invalido@example.com")
						.param("senha", "senha-segura-123")
						.param("tipo", "PJ")
						.param("cpf", "529.982.247-25")
						.param("documento", "1234567890123"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/registo"));

		assertNull(usuarios.findByEmail("empresa-cnpj-invalido@example.com"));
	}

	@Test
	@Transactional
	void cadastroDeCidadaoExigeEArmazenaCpfValido() throws Exception {
		mockMvc.perform(post("/fazer-registo")
						.param("nome", "Cidadão de teste")
						.param("email", "cidadao-com-cpf@example.com")
						.param("senha", "senha-segura-123")
						.param("tipo", "Cidadao")
						.param("documento", "1990-01-01")
						.param("cpf", "52998224725"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));

		Usuario usuario = usuarios.findByEmail("cidadao-com-cpf@example.com");
		assertEquals("529.982.247-25", usuario.getCpf());

		mockMvc.perform(post("/fazer-registo")
						.param("nome", "Cidadão sem CPF")
						.param("email", "cidadao-sem-cpf@example.com")
						.param("senha", "senha-segura-123")
						.param("tipo", "Cidadao")
						.param("documento", "1990-01-01"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/registo"));
		assertNull(usuarios.findByEmail("cidadao-sem-cpf@example.com"));
	}

	@Test
	@Transactional
	void usuarioConfirmaPresencaEEncontraIngressoQrEmMeusEventos() throws Exception {
		Usuario usuario = usuarios.saveAndFlush(new Cidadao(
				"Participante de teste", "participante-teste@example.com", "senha", LocalDate.of(1990, 1, 1)));
		Evento evento = eventos.saveAndFlush(new Evento(
				"Evento para inscrição", "Local de teste", "Geral",
				LocalDateTime.of(2026, 12, 2, 19, 0), "Descrição de teste",
				"participante-teste@example.com", 5));
		MockHttpSession sessao = new MockHttpSession();
		sessao.setAttribute("usuarioLogado", usuario);
		sessao.setAttribute("usuarioNome", usuario.getNome());
		sessao.setAttribute("usuarioEmail", usuario.getEmail());

		mockMvc.perform(post("/participar/{id}", evento.getId()).session(sessao))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mural"))
				.andExpect(flash().attribute("sucessoInscricao", true))
				.andExpect(flash().attribute("mensagemMural",
						"Sucesso! Sua vaga foi confirmada. Agradecemos pela sua inscrição! Acesse mais detalhes dos seus eventos confirmados na aba Meus eventos."));

		mockMvc.perform(get("/mural").session(sessao))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Meus eventos")))
				.andExpect(content().string(containsString("Mais detalhes")));

		mockMvc.perform(get("/mural")
						.session(sessao)
						.flashAttr("sucessoInscricao", true)
						.flashAttr("mensagemMural", "Sucesso! Sua vaga foi confirmada. Agradecemos pela sua inscrição! Acesse mais detalhes dos seus eventos confirmados na aba Meus eventos."))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("confirmacaoInscricao")))
				.andExpect(content().string(containsString("Sucesso! Sua vaga foi confirmada.")))
				.andExpect(content().string(containsString("O envio por e-mail ainda não está ativado.")));

		mockMvc.perform(get("/meus-eventos").session(sessao))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Eventos confirmados")))
				.andExpect(content().string(containsString("Eventos criados por você")))
				.andExpect(content().string(containsString("QR Code de ingresso")))
				.andExpect(content().string(containsString("envio por e-mail ainda não está ativado")));

		Ingresso ingresso = ingressos.findByEvento_IdAndUsuario_Id(evento.getId(), usuario.getId()).orElseThrow();
		mockMvc.perform(get("/ingressos/validar/{token}", ingresso.getToken()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Ingresso válido")))
				.andExpect(content().string(containsString("Participante de teste")));

		MvcResult qrResponse = mockMvc.perform(get("/ingressos/{token}/qr", ingresso.getToken()).session(sessao))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
				.andReturn();
		assertTrue(qrResponse.getResponse().getContentAsByteArray().length > 0);

		mockMvc.perform(get("/ingressos/{token}/baixar", ingresso.getToken()).session(sessao))
				.andExpect(status().isSeeOther())
				.andExpect(header().string("Location", "/perfil"));

		mockMvc.perform(post("/perfil/cpf").session(sessao).param("cpf", "529.982.247-25"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/ingressos/" + ingresso.getToken() + "/baixar"));

		MvcResult arquivoIngresso = mockMvc.perform(get("/ingressos/{token}/baixar", ingresso.getToken()).session(sessao))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"ingresso-mwk.pdf\""))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
				.andReturn();
		byte[] pdfIngresso = arquivoIngresso.getResponse().getContentAsByteArray();
		assertTrue(new String(pdfIngresso, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-"));
		try (PDDocument documentoIngresso = Loader.loadPDF(pdfIngresso)) {
			String textoIngresso = new PDFTextStripper().getText(documentoIngresso);
			assertTrue(textoIngresso.contains("Participante de teste"));
			assertTrue(textoIngresso.contains("529.982.247-25"));
			assertTrue(textoIngresso.contains("Apresente este QR Code na entrada do evento."));
			boolean qrEmbutido = false;
			for (var nomeRecurso : documentoIngresso.getPage(0).getResources().getXObjectNames()) {
				if (documentoIngresso.getPage(0).getResources().getXObject(nomeRecurso) instanceof PDImageXObject) {
					qrEmbutido = true;
					break;
				}
			}
			assertTrue(qrEmbutido);
		}
	}

	@Test
	void usuarioPodeSalvarCpfNoPerfil() throws Exception {
		Usuario usuario = usuarios.saveAndFlush(new Cidadao(
				"Pessoa do perfil", "cpf-perfil@example.com", "senha", LocalDate.of(1990, 1, 1)));
		MockHttpSession sessao = new MockHttpSession();
		sessao.setAttribute("usuarioEmail", usuario.getEmail());

		mockMvc.perform(post("/perfil/cpf").session(sessao).param("cpf", "52998224725"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/perfil"));

		assertEquals("529.982.247-25", usuarios.findById(usuario.getId()).orElseThrow().getCpf());

		mockMvc.perform(post("/perfil/cpf").session(sessao).param("cpf", "11111111111"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/perfil"));
		assertEquals("529.982.247-25", usuarios.findById(usuario.getId()).orElseThrow().getCpf());
	}

	@Test
	@Transactional
	void solicitarIngressoPagoRegistraParticipanteSemProcessarPagamento() throws Exception {
		Usuario usuario = usuarios.saveAndFlush(new Cidadao(
				"Participante ingresso", "participante-ingresso@example.com", "senha", LocalDate.of(1990, 1, 1)));
		Evento evento = eventos.saveAndFlush(new Evento(
				"Evento pago para solicitar",
				"Local de teste",
				"Cultura",
				LocalDateTime.of(2026, 12, 8, 19, 0),
				"Descrição",
				"produtor-ingresso@example.com",
				10,
				null,
				true,
				new BigDecimal("35.00")));
		MockHttpSession sessao = new MockHttpSession();
		sessao.setAttribute("usuarioLogado", usuario);

		mockMvc.perform(post("/participar/{id}", evento.getId()).session(sessao))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mural"));

		assertTrue(eventos.findById(evento.getId()).orElseThrow().isParticipante(usuario));
	}

	private MockMultipartFile imagemValida(String nomeCampo, String nomeArquivo) throws IOException {
		BufferedImage imagem = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream conteudo = new ByteArrayOutputStream();
		ImageIO.write(imagem, "png", conteudo);
		return new MockMultipartFile(nomeCampo, nomeArquivo, "image/png", conteudo.toByteArray());
	}

}
