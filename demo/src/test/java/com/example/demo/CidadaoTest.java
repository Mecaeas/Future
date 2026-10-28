package com.example.demo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CidadaoTest {

    @Test
    void defaultConstructorLeavesPropertiesUninitialized() {
        Cidadao cidadao = new Cidadao();

        assertAll(
                () -> assertNull(cidadao.getId()),
                () -> assertNull(cidadao.getNome()),
                () -> assertNull(cidadao.getEmail()),
                () -> assertNull(cidadao.getSenha()),
                () -> assertNull(cidadao.getDataNascimento())
        );
    }

    @Test
    void parameterizedConstructorStoresCitizenAndAgeProperties() {
        LocalDate dataNascimento = LocalDate.of(1997, 4, 12);
        Cidadao cidadao = new Cidadao("Ana Silva", "ana@example.com", "segredo", dataNascimento);

        assertAll(
                () -> assertNull(cidadao.getId()),
                () -> assertEquals("Ana Silva", cidadao.getNome()),
                () -> assertEquals("ana@example.com", cidadao.getEmail()),
                () -> assertEquals("segredo", cidadao.getSenha()),
                () -> assertEquals(dataNascimento, cidadao.getDataNascimento())
        );
    }
}