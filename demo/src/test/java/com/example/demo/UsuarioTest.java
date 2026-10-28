package com.example.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UsuarioTest {

    @Test
    void defaultConstructorLeavesPropertiesUninitialized() {
        Usuario usuario = new TestUsuario();

        assertAll(
                () -> assertNull(usuario.getId()),
                () -> assertNull(usuario.getNome()),
                () -> assertNull(usuario.getEmail()),
                () -> assertNull(usuario.getSenha())
        );
    }

    @Test
    void parameterizedConstructorStoresUserProperties() {
        Usuario usuario = new TestUsuario("Ana Silva", "ana@example.com", "segredo");

        assertAll(
                () -> assertNull(usuario.getId()),
                () -> assertEquals("Ana Silva", usuario.getNome()),
                () -> assertEquals("ana@example.com", usuario.getEmail()),
                () -> assertEquals("segredo", usuario.getSenha())
        );
    }

    private static final class TestUsuario extends Usuario {
        private TestUsuario() {}

        private TestUsuario(String nome, String email, String senha) {
            super(nome, email, senha);
        }
    }
}