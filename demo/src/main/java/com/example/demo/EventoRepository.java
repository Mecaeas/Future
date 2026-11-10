package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface EventoRepository extends JpaRepository<Evento, Long> {
    List<Evento> findAllByOrderByDataHoraAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select evento from Evento evento where evento.id = :id")
    Optional<Evento> findByIdForUpdate(@Param("id") Long id);
}