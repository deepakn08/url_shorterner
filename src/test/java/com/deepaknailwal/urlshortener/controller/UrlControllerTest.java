package com.deepaknailwal.urlshortener.controller;

import com.deepaknailwal.urlshortener.dto.CreateUrlRequest;
import com.deepaknailwal.urlshortener.dto.UrlResponse;
import com.deepaknailwal.urlshortener.exception.AliasAlreadyExistsException;
import com.deepaknailwal.urlshortener.exception.BadRequestException;
import com.deepaknailwal.urlshortener.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UrlController.class)
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlService urlService;

    @Test
    void createShortUrl_validRequest_returns201WithShortCode() throws Exception {
        when(urlService.createShortUrl(any()))
                .thenReturn(new UrlResponse("abc1234", "http://localhost:8080/abc1234", "https://example.com"));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.short_code").value("abc1234"));
    }

    @Test
    void createShortUrl_blankUrl_returns400() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShortUrl_malformedUrl_returns400() throws Exception {
        when(urlService.createShortUrl(any())).thenThrow(new BadRequestException("original_url must be a well-formed URL"));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createShortUrl_aliasTaken_returns409() throws Exception {
        when(urlService.createShortUrl(any())).thenThrow(new AliasAlreadyExistsException("custom_alias 'taken' is already in use"));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"https://example.com\",\"custom_alias\":\"taken\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void createShortUrl_invalidAliasFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"https://example.com\",\"custom_alias\":\"a\"}"))
                .andExpect(status().isBadRequest());
    }
}
