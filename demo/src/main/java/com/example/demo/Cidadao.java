package com.example.demo;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import java.time.LocalDate;

@Entity
@DiscriminatorValue("CIDADAO")
public class Cidadao extends Usuario {

    private LocalDate dataNascimento;

    // Construtor vazio obrigatório para o JPA/Hibernate
    public Cidadao() {
        super();
    }

    public Cidadao(String nome, String email, String senha, LocalDate dataNascimento) {
        super(nome, email, senha);
        this.dataNascimento = dataNascimento;
    }

    // Getter
    public LocalDate getDataNascimento() { 
        return dataNascimento; 
    }

    // Setter OBRIGATÓRIO para o Spring Boot conseguir salvar e carregar do banco
    public void setDataNascimento(LocalDate dataNascimento) { 
        this.dataNascimento = dataNascimento; 
    }
}