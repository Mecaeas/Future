package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

@Controller
public class UsuarioController {

    private static final DateTimeFormatter FORMATO_DATA_NASCIMENTO = DateTimeFormatter
            .ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    @Autowired
    private UsuarioRepository repositorio;

    // Mostra a página de Login
    @GetMapping("/login")
    public String paginaLogin() {
        return "login";
    }

    @GetMapping("/recuperar-senha")
    public String paginaRecuperacao() {
        return "recuperar-senha";
    }

    @PostMapping("/recuperar-senha")
    public String recuperarSenha(@RequestParam("email") String email,
                                 @RequestParam("documento") String documento,
                                 @RequestParam("novaSenha") String novaSenha,
                                 @RequestParam("confirmarSenha") String confirmarSenha,
                                 Model model,
                                 RedirectAttributes mensagens) {
        model.addAttribute("email", email);
        if (novaSenha == null || novaSenha.length() < 8) {
            model.addAttribute("erroRecuperacao", "A nova senha deve ter pelo menos 8 caracteres.");
            return "recuperar-senha";
        }
        if (!novaSenha.equals(confirmarSenha)) {
            model.addAttribute("erroRecuperacao", "A confirmação da senha não corresponde.");
            return "recuperar-senha";
        }

        Usuario usuario = repositorio.findByEmail(email);
        boolean documentoConfere = false;
        if (usuario instanceof Cidadao cidadao) {
            try {
                documentoConfere = cidadao.getDataNascimento().equals(LocalDate.parse(documento, FORMATO_DATA_NASCIMENTO));
            } catch (DateTimeParseException erro) {
                documentoConfere = false;
            }
        } else if (usuario instanceof PessoaJuridica pessoaJuridica) {
            documentoConfere = pessoaJuridica.getCnpj() != null && pessoaJuridica.getCnpj().trim().equals(documento.trim());
        }

        if (!documentoConfere) {
            model.addAttribute("erroRecuperacao", "Não foi possível confirmar os dados. Confira e tente novamente.");
            return "recuperar-senha";
        }

        usuario.setSenha(passwordEncoder.encode(novaSenha));
        repositorio.save(usuario);
        mensagens.addFlashAttribute("mensagemLogin", "Senha redefinida. Entre com a nova senha.");
        return "redirect:/login";
    }

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private boolean senhaCorresponde(Usuario usuario, String senhaInformada) {
        if (usuario == null || usuario.getSenha() == null || senhaInformada == null) {
            return false;
        }

        return passwordEncoder.matches(senhaInformada, usuario.getSenha())
                || usuario.getSenha().equals(senhaInformada);
    }

    private Usuario buscarUsuarioPorDocumento(String documento) {
        if (documento == null || documento.trim().isEmpty()) {
            return null;
        }

        String documentoLimpo = documento.replaceAll("\\D", "");
        if (documentoLimpo.isEmpty()) {
            return null;
        }

        for (Usuario usuario : repositorio.findAll()) {
            if (usuario instanceof Cidadao cidadao) {
                if (cidadao.getDataNascimento() == null) {
                    continue;
                }

                String nascimentoFormatado = cidadao.getDataNascimento().format(FORMATO_DATA_NASCIMENTO);
                String nascimentoSemMascara = cidadao.getDataNascimento().format(DateTimeFormatter.ofPattern("ddMMyyyy"));
                if (documento.trim().equals(nascimentoFormatado) || documentoLimpo.equals(nascimentoSemMascara)) {
                    return usuario;
                }
            } else if (usuario instanceof PessoaJuridica pessoaJuridica) {
                String cnpj = pessoaJuridica.getCnpj() != null ? pessoaJuridica.getCnpj().trim() : "";
                if (cnpj.equals(documento.trim()) || cnpj.replaceAll("\\D", "").equals(documentoLimpo)) {
                    return usuario;
                }
            }
        }

        return null;
    }

    // Processa os dados quando o utilizador clica em "Entrar"
    @PostMapping({"/login", "/fazer-login"})
    public String fazerLogin(@RequestParam(value = "email", required = false) String email,
                             @RequestParam(value = "documento", required = false) String documento,
                             @RequestParam("senha") String senha,
                             Model model,
                             HttpSession session) {

        String inputLimpo = (email != null && !email.trim().isEmpty())
                ? email.trim().toLowerCase()
                : (documento != null ? documento.trim() : "");

        Usuario usuario = null;
        if (!inputLimpo.isEmpty()) {
            usuario = repositorio.findByEmail(inputLimpo);
        }

        if (usuario == null && documento != null && !documento.trim().isEmpty()) {
            usuario = buscarUsuarioPorDocumento(documento);
        }

        if (usuario == null && !inputLimpo.isEmpty()) {
            for (Usuario u : repositorio.findAll()) {
                if (u instanceof PessoaJuridica pj) {
                    String cnpj = pj.getCnpj() != null ? pj.getCnpj().trim() : "";
                    if (cnpj.equals(inputLimpo) && senhaCorresponde(pj, senha)) {
                        usuario = pj;
                        break;
                    }
                }
            }
        }

        if (usuario != null && senhaCorresponde(usuario, senha)) {
            session.setAttribute("usuarioLogado", usuario);
            session.setAttribute("usuarioEmail", usuario.getEmail());
            session.setAttribute("usuarioNome", usuario.getNome());
            return "redirect:/mural";
        }

        model.addAttribute("erro", "E-mail, CNPJ ou senha incorretos.");
        return "login";
    }

    // Mostra a página de Registo
    @GetMapping("/registo")
    public String paginaRegisto() {
        return "registo";
    }

    // Processa os dados quando o utilizador clica em "Registar"
    @PostMapping("/fazer-registo")
    public String fazerRegisto(@RequestParam("nome") String nome,
                               @RequestParam("email") String email,
                               @RequestParam("senha") String senha,
                               @RequestParam("tipo") String tipo,
                               @RequestParam("documento") String documento,
                               @RequestParam(value = "cpf", required = false) String cpf,
                               RedirectAttributes mensagens) {
        
        String emailLimpo = (email != null) ? email.trim().toLowerCase() : "";
        String docLimpo = (documento != null) ? documento.trim() : "";
        String cpfFormatado = CpfUtils.formatarSeValido(cpf);
        if (cpfFormatado == null) {
            mensagens.addFlashAttribute("erroRegisto", "Informe um CPF válido com 11 dígitos.");
            return "redirect:/registo";
        }

        // 1. Verificar se o e-mail já existe
        if (repositorio.findByEmail(emailLimpo) != null) {
            mensagens.addFlashAttribute("erroRegisto", "Este e-mail já está cadastrado.");
            return "redirect:/registo";
        }

        // 2. Verificar o tamanho mínimo da senha
        if (senha == null || senha.length() < 8) {
            mensagens.addFlashAttribute("erroRegisto", "A senha deve ter pelo menos 8 caracteres.");
            return "redirect:/registo";
        }

        // 3. Cadastrar Cidadão ou PJ
        Usuario novoUsuario;
        if ("Cidadao".equalsIgnoreCase(tipo)) {
            LocalDate dataNascimento;
            try {
                try {
                    dataNascimento = LocalDate.parse(docLimpo);
                } catch (DateTimeParseException formatoLegado) {
                    dataNascimento = LocalDate.parse(docLimpo, FORMATO_DATA_NASCIMENTO);
                }
            } catch (DateTimeParseException erro) {
                mensagens.addFlashAttribute("erroRegisto", "Informe uma data de nascimento válida.");
                return "redirect:/registo";
            }
            if (dataNascimento.isAfter(LocalDate.now())) {
                mensagens.addFlashAttribute("erroRegisto", "A data de nascimento não pode ser futura.");
                return "redirect:/registo";
            }
            String senhaCriptografada = passwordEncoder.encode(senha);
            novoUsuario = new Cidadao(nome, emailLimpo, senhaCriptografada, dataNascimento);
        } else {
            String cnpjInformado = docLimpo;
            String cnpj = docLimpo.replaceAll("\\D", "");
            boolean cnpjSomenteDigitos = cnpjInformado.matches("\\d{14}");
            boolean cnpjComMascara = cnpjInformado.matches("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}");
            if (!cnpj.matches("\\d{14}") || (!cnpjSomenteDigitos && !cnpjComMascara)) {
                mensagens.addFlashAttribute("erroRegisto", "Informe um CNPJ com exatamente 14 dígitos.");
                return "redirect:/registo";
            }
            String cnpjFormatado = cnpj.replaceFirst(
                    "(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
            String senhaCriptografada = passwordEncoder.encode(senha);
            novoUsuario = new PessoaJuridica(nome, emailLimpo, senhaCriptografada, cnpjFormatado);
        }
        novoUsuario.setCpf(cpfFormatado);
        repositorio.save(novoUsuario);
        
        mensagens.addFlashAttribute("mensagemLogin", "Conta criada com sucesso! Faça login.");
        return "redirect:/login";
    }

    @GetMapping("/perfil")
    public String perfil(HttpSession session, Model model) {
        Usuario usuario = usuarioDaSessao(session);
        if (usuario == null) {
            session.invalidate();
            return "redirect:/login";
        }

        model.addAttribute("usuario", usuario);
        Object erroCpf = session.getAttribute("erroCpf");
        if (erroCpf != null) {
            model.addAttribute("erroCpf", erroCpf);
            session.removeAttribute("erroCpf");
        }
        return "perfil";
    }

    @PostMapping("/perfil/cpf")
    public String salvarCpf(@RequestParam(value = "cpf", required = false) String cpf, HttpSession session,
                            RedirectAttributes mensagens) {
        Usuario usuario = usuarioDaSessao(session);
        if (usuario == null) {
            session.invalidate();
            return "redirect:/login";
        }

        String cpfFormatado = CpfUtils.formatarSeValido(cpf);
        if (cpfFormatado == null) {
            mensagens.addFlashAttribute("erroCpf", "Informe um CPF válido com 11 dígitos.");
        } else {
            usuario.setCpf(cpfFormatado);
            repositorio.save(usuario);
            Object ingressoPendente = session.getAttribute("ingressoPendenteCpf");
            if (ingressoPendente instanceof String token && !token.isBlank()) {
                session.removeAttribute("ingressoPendenteCpf");
                return "redirect:/ingressos/" + token + "/baixar";
            }
            mensagens.addFlashAttribute("sucessoCpf", "CPF salvo. Agora você pode baixar seu ingresso.");
        }
        return "redirect:/perfil";
    }
// Exemplo de atualização no UsuarioController.java

@GetMapping("/perfil/alterar-senha")
public String telaAlterarSenha(HttpSession session, Model model) {
    Usuario usuario = usuarioDaSessao(session);
    if (usuario == null) {
        session.invalidate();
        return "redirect:/login";
    }
    model.addAttribute("usuario", usuario);
    return "alterar-senha";
}

@GetMapping("/perfil/alterar-email")
public String telaAlterarEmail(HttpSession session, Model model) {
    Usuario usuario = usuarioDaSessao(session);
    if (usuario == null) {
        session.invalidate();
        return "redirect:/login";
    }
    model.addAttribute("usuario", usuario);
    return "alterar-email";
}

    @PostMapping("/perfil/email")
    public String alterarEmail(@RequestParam("senhaAtual") String senhaAtual,
                               @RequestParam("novoEmail") String novoEmail,
                               @RequestParam("confirmarEmail") String confirmarEmail,
                               HttpSession session, 
                               RedirectAttributes mensagens) {
        Usuario usuario = usuarioDaSessao(session);
        if (usuario == null) {
            session.invalidate();
            return "redirect:/login";
        }

        String emailNormalizado = novoEmail == null ? "" : novoEmail.trim().toLowerCase();
        if (!senhaCorresponde(usuario, senhaAtual)) {
            mensagens.addFlashAttribute("erroEmail", "A senha atual está incorreta.");
        } else if (!emailNormalizado.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            mensagens.addFlashAttribute("erroEmail", "Informe um endereço de e-mail válido.");
        } else if (!emailNormalizado.equals(confirmarEmail == null ? "" : confirmarEmail.trim().toLowerCase())) {
            mensagens.addFlashAttribute("erroEmail", "A confirmação do e-mail não confere.");
        } else {
            Usuario existente = repositorio.findByEmail(emailNormalizado);
            if (existente != null && !existente.getId().equals(usuario.getId())) {
                mensagens.addFlashAttribute("erroEmail", "Este e-mail já está cadastrado.");
            } else {
                usuario.setEmail(emailNormalizado);
                repositorio.save(usuario);
                session.setAttribute("usuarioEmail", emailNormalizado);
                mensagens.addFlashAttribute("sucessoEmail", "E-mail alterado com sucesso.");
            }
        }

        return "redirect:/perfil/alterar-email";
    }

    @PostMapping("/sair")
    public String sair(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    private Usuario usuarioDaSessao(HttpSession session) {
        Object email = session.getAttribute("usuarioEmail");
        return email == null ? null : repositorio.findByEmail(email.toString());
    }
}
