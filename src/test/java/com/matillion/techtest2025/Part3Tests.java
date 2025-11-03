package com.matillion.techtest2025;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matillion.techtest2025.model.ColumnProfile;
import com.matillion.techtest2025.repository.DataAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Part3Tests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataAnalysisRepository dataAnalysisRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        dataAnalysisRepository.deleteAll();
    }

    @Test
    void shouldReturnColumnProfilesWithTypesAndNumericSummaries(
            @Value("classpath:test-data/large.csv") Resource largeCsv
    ) throws Exception {
        String csv = largeCsv.getContentAsString(UTF_8);

        mockMvc.perform(post("/api/analysis/ingestCsv")
                        .contentType(TEXT_PLAIN)
                        .content(csv))
                .andExpect(status().isOk());

        var entities = dataAnalysisRepository.findAll();
        Long id = entities.getFirst().getId();

        var result = mockMvc.perform(get("/api/analysis/{id}/profile", id))
                .andExpect(status().isOk())
                .andReturn();

        List<ColumnProfile> profiles = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<List<ColumnProfile>>() {}
        );

        assertThat(profiles).hasSize(6);
        assertThat(profiles.stream().anyMatch(p -> p.columnName().equals("number") && p.min() != null && p.max() != null && p.mean() != null)).isTrue();
        assertThat(profiles.stream().anyMatch(p -> p.columnName().equals("driver") && p.min() == null && p.max() == null && p.mean() == null)).isTrue();
    }
}


