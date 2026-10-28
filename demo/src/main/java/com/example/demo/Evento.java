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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

    @Column(length = 2000)
    private String descricao;

    @Column(nullable = false)
    private String criadoPorEmail;

    @ElementCollection
    @CollectionTable(name = "evento_imagens", joinColumns = @JoinColumn(name = "evento_id"))
    @OrderColumn(name = "ordem")
    @Column(name = "caminho", nullable = false)
    private List<String> imagens = new ArrayList<>();

    public Evento() {}

    public Evento(String nome, String endereco, String categoria, LocalDateTime dataHora,
                  String descricao, String criadoPorEmail) {
        this.nome = nome;
        this.endereco = endereco;
        this.categoria = categoria;
        this.dataHora = dataHora;
        this.descricao = descricao;
        this.criadoPorEmail = criadoPorEmail;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getEndereco() { return endereco; }
    public String getCategoria() { return categoria; }
    public LocalDateTime getDataHora() { return dataHora; }
    public String getDataHoraFormatada() { return dataHora.format(FORMATO_DATA); }
    public String getDescricao() { return descricao; }
    public String getCriadoPorEmail() { return criadoPorEmail; }
    public List<String> getImagens() { return imagens; }

    public void adicionarImagem(String caminho) {
        imagens.add(caminho);
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

    public String getResumoParticipantes() {
        int quantidade = getQuantidadeParticipantes();
        return quantidade == 1
                ? "1 pessoa confirmada"
                : quantidade + " pessoas confirmadas";
    }

}