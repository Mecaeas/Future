package com.example.demo;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Entity
public class Evento {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String endereco;

    @Column(nullable = false)
    private String categoria;

    @Column(nullable = false)
    private LocalDateTime dataHora;

    @Column
    private LocalDate dataFim;

    @Column
    private LocalTime horaFim;

    @Column
    private Boolean pago;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorIngresso;

    @Column(length = 2000)
    private String descricao;

    @Column(length = 500)
    private String informacaoOrganizador;

    @Column(nullable = false)
    private String criadoPorEmail;

    @Column
    private Integer limiteParticipantes;

    @Column(length = 255)
    private String imagemCapa;

    @ElementCollection
    @CollectionTable(name = "evento_imagens", joinColumns = @JoinColumn(name = "evento_id"))
    @OrderColumn(name = "ordem")
    @Column(name = "caminho", nullable = false)
    private List<String> imagens = new ArrayList<>();

    public Evento() {}

    public Evento(String nome, String endereco, String categoria, LocalDateTime dataHora,
                  String descricao, String criadoPorEmail) {
        this(nome, endereco, categoria, dataHora, descricao, criadoPorEmail, null);
    }

    public Evento(String nome, String endereco, String categoria, LocalDateTime dataHora,
                  String descricao, String criadoPorEmail, Integer limiteParticipantes) {
        this(nome, endereco, categoria, dataHora, descricao, criadoPorEmail, limiteParticipantes, null);
    }

    public Evento(String nome, String endereco, String categoria, LocalDateTime dataHora,
                  String descricao, String criadoPorEmail, Integer limiteParticipantes,
                  LocalDate dataFim) {
        this(nome, endereco, categoria, dataHora, descricao, criadoPorEmail, limiteParticipantes,
                dataFim, false, null);
    }

    public Evento(String nome, String endereco, String categoria, LocalDateTime dataHora,
                  String descricao, String criadoPorEmail, Integer limiteParticipantes,
                  LocalDate dataFim, boolean pago, BigDecimal valorIngresso) {
        this.nome = nome;
        this.endereco = endereco;
        this.categoria = categoria;
        this.dataHora = dataHora;
        this.dataFim = dataFim;
        this.pago = pago;
        this.valorIngresso = valorIngresso;
        this.descricao = descricao;
        this.criadoPorEmail = criadoPorEmail;
        this.limiteParticipantes = limiteParticipantes;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getEndereco() { return endereco; }
    public String getCategoria() { return categoria; }
    public LocalDateTime getDataHora() { return dataHora; }
    public LocalDate getDataFim() { return dataFim; }
    public LocalTime getHoraFim() { return horaFim; }
    public boolean isPago() { return Boolean.TRUE.equals(pago); }
    public BigDecimal getValorIngresso() { return valorIngresso; }
    public String getValorIngressoFormatado() {
        return valorIngresso == null ? "" : NumberFormat.getCurrencyInstance(new Locale("pt", "BR"))
                .format(valorIngresso);
    }
    public String getDataHoraFormatada() {
        String inicio = dataHora.format(FORMATO_DATA);
        if (horaFim != null) {
            String termino = horaFim.format(DateTimeFormatter.ofPattern("HH:mm"));
            if (dataFim != null) {
                return inicio + " até " + dataFim.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + " às " + termino;
            }
            return inicio + " até " + termino;
        }
        return dataFim == null
                ? inicio
                : inicio + " até " + dataFim.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
    public String getDataHoraMuralFormatada() {
        if (dataFim == null && horaFim == null) {
            return getDataHoraFormatada();
        }
        String inicio = dataHora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String horarioInicio = dataHora.format(DateTimeFormatter.ofPattern("HH:mm"));
        if (dataFim == null) {
            return inicio + " das " + horarioInicio + " às "
                    + horaFim.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        if (horaFim != null) {
            return inicio + " até " + dataFim.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    + " das " + horarioInicio + " às "
                    + horaFim.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return dataHora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " até " + dataFim.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " às " + dataHora.format(DateTimeFormatter.ofPattern("HH:mm"));
    }
    public String getDescricao() { return descricao; }
    public String getInformacaoOrganizador() { return informacaoOrganizador; }
    public String getCriadoPorEmail() { return criadoPorEmail; }
    public Integer getLimiteParticipantes() { return limiteParticipantes; }
    public List<String> getImagens() { return imagens; }
    public String getImagemCapa() {
        return imagemCapa != null ? imagemCapa : imagens.isEmpty() ? null : imagens.get(0);
    }
    public List<String> getImagensGaleria() {
        String caminhoCapa = getImagemCapa();
        int primeiraFotoGaleria = imagemCapa == null && !imagens.isEmpty() ? 1 : 0;
        List<String> galeria = new ArrayList<>();
        for (int indice = primeiraFotoGaleria; indice < imagens.size(); indice++) {
            String caminho = imagens.get(indice);
            if (!Objects.equals(caminhoCapa, caminho)) {
                galeria.add(caminho);
            }
        }
        return galeria;
    }
    public List<String> getTodosCaminhosImagens() {
        List<String> caminhos = new ArrayList<>();
        if (imagemCapa != null) {
            caminhos.add(imagemCapa);
        } else if (!imagens.isEmpty()) {
            caminhos.add(imagens.get(0));
        }
        caminhos.addAll(getImagensGaleria());
        return caminhos;
    }

    public boolean foiCriadoPor(String email) {
        return email != null
                && criadoPorEmail != null
                && criadoPorEmail.trim().equalsIgnoreCase(email.trim());
    }

    public void atualizar(String nome, String endereco, String categoria, LocalDateTime dataHora,
                          LocalDate dataFim, String descricao, Integer limiteParticipantes) {
        atualizar(nome, endereco, categoria, dataHora, dataFim, descricao, limiteParticipantes, false, null);
    }

    public void atualizar(String nome, String endereco, String categoria, LocalDateTime dataHora,
                          LocalDate dataFim, String descricao, Integer limiteParticipantes,
                          boolean pago, BigDecimal valorIngresso) {
        atualizar(nome, endereco, categoria, dataHora, dataFim, descricao,
                limiteParticipantes, pago, valorIngresso, informacaoOrganizador);
    }

    public void atualizar(String nome, String endereco, String categoria, LocalDateTime dataHora,
                          LocalDate dataFim, String descricao, Integer limiteParticipantes,
                          boolean pago, BigDecimal valorIngresso, String informacaoOrganizador) {
        atualizar(nome, endereco, categoria, dataHora, dataFim, null, descricao, limiteParticipantes,
                pago, valorIngresso, informacaoOrganizador);
    }

    public void atualizar(String nome, String endereco, String categoria, LocalDateTime dataHora,
                          LocalDate dataFim, LocalTime horaFim, String descricao, Integer limiteParticipantes,
                          boolean pago, BigDecimal valorIngresso, String informacaoOrganizador) {
        this.nome = nome;
        this.endereco = endereco;
        this.categoria = categoria;
        this.dataHora = dataHora;
        this.dataFim = dataFim;
        this.horaFim = horaFim;
        this.pago = pago;
        this.valorIngresso = valorIngresso;
        this.descricao = descricao;
        this.informacaoOrganizador = informacaoOrganizador;
        this.limiteParticipantes = limiteParticipantes;
    }

    public void definirImagemCapa(String caminho) {
        this.imagemCapa = caminho;
    }

    public void definirHoraFim(LocalTime horaFim) {
        this.horaFim = horaFim;
    }

    public void definirInformacaoOrganizador(String informacaoOrganizador) {
        this.informacaoOrganizador = informacaoOrganizador;
    }

    public void adicionarImagem(String caminho) {
        imagens.add(caminho);
    }

    public void substituirImagens(List<String> caminhos) {
        imagens.clear();
        imagens.addAll(caminhos);
    }

    @ManyToMany
    private List<Usuario> participantes = new ArrayList<>();

    public List<Usuario> getParticipantes() { 
    return participantes; 
    }

    public boolean isParticipante(Usuario usuario) {
        if (usuario == null || usuario.getId() == null) {
            return false;
        }

        return participantes.stream()
                .anyMatch(participante -> participante != null
                        && participante.getId() != null
                        && participante.getId().equals(usuario.getId()));
    }

    public int getQuantidadeParticipantes() {
        return participantes == null ? 0 : participantes.size();
    }

    public boolean isLotacaoEsgotada() {
        return limiteParticipantes != null && getQuantidadeParticipantes() >= limiteParticipantes;
    }

    public boolean isTemVagasDisponiveis() {
        return !isLotacaoEsgotada();
    }

    public int getVagasRestantes() {
        if (limiteParticipantes == null) {
            return -1;
        }
        return Math.max(0, limiteParticipantes - getQuantidadeParticipantes());
    }

    public boolean deveMostrarVagasRestantes() {
        return limiteParticipantes != null
                && getQuantidadeParticipantes() >= Math.ceil(limiteParticipantes * 0.9);
    }

    public String getMensagemVagasRestantes() {
        if (!deveMostrarVagasRestantes()) {
            return "";
        }

        int vagasRestantes = getVagasRestantes();
        if (vagasRestantes <= 0) {
            return "Evento lotado.";
        }
        if (vagasRestantes == 1) {
            return "Última vaga disponível!";
        }
        if (vagasRestantes <= 30) {
            return "Restam " + vagasRestantes + " vagas.";
        }
        return "";
    }

    public String getResumoParticipantes() {
        int quantidade = getQuantidadeParticipantes();
        return quantidade == 1
                ? "1 pessoa confirmada"
                : quantidade + " pessoas confirmadas";
    }

}