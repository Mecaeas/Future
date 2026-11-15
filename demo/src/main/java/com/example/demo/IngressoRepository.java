package com.example.demo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngressoRepository extends JpaRepository<Ingresso, Long> {

    Optional<Ingresso> findByTokenAndUsuario_Id(String token, Long usuarioId);

    Optional<Ingresso> findByToken(String token);

    Optional<Ingresso> findByEvento_IdAndUsuario_Id(Long eventoId, Long usuarioId);

    void deleteByEvento_Id(Long eventoId);

    void deleteByEvento_IdAndUsuario_Id(Long eventoId, Long usuarioId);
}
