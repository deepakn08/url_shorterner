package com.deepaknailwal.urlshortener.controller;

import com.deepaknailwal.urlshortener.exception.ResourceNotFoundException;
import com.deepaknailwal.urlshortener.model.Url;
import com.deepaknailwal.urlshortener.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UrlService urlService;

    @Test
    void redirect_knownCode_returns301WithLocationHeader() throws Exception {
        Url url = Url.builder()
                .shortCode("abc1234")
                .originalUrl("https://example.com/target")
                .active(true)
                .build();
        when(urlService.getActiveUrlByShortCode("abc1234")).thenReturn(url);

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://example.com/target"));
    }

    @Test
    void redirect_unknownCode_returns404() throws Exception {
        when(urlService.getActiveUrlByShortCode("missing"))
                .thenThrow(new ResourceNotFoundException("Short URL not found: missing"));

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound());
    }
}
