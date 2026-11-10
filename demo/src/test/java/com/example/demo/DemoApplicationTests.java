package com.example.demo;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DemoApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EventoRepository eventos;

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void muralExibeVagasDoEvento() throws Exception {
		eventos.save(new Evento(
				"Evento de teste",
				"Local de teste",
				"Geral",
				LocalDateTime.of(2026, 12, 1, 19, 0),
				"Descrição de teste",
				"criador@example.com",
				5));

		mockMvc.perform(get("/mural"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("0 de 5 vagas preenchidas")));
	}

}
