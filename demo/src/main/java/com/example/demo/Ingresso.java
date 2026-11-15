package com.example.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"evento_id", "usuario_id"}))
public class Ingresso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario usuario;

    protected Ingresso() {}

    public Ingresso(Evento evento, Usuario usuario) {
        this.token = UUID.randomUUID().toString();
        this.evento = evento;
        this.usuario = usuario;
    }

    public Long getId() { return id; }
    public String getToken() { return token; }
    public Evento getEvento() { return evento; }
    public Usuario getUsuario() { return usuario; }
}
