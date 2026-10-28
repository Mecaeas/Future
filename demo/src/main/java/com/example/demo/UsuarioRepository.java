package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    
    // Procura um utilizador pelo E-mail
    Usuario findByEmail(String email);

    // Procura um utilizador pelo E-mail e Senha (para fazer Login)
    Usuario findByEmailAndSenha(String email, String senha);
}