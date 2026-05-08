package com.vibedev.controller;

import com.vibedev.dto.search.SearchSuggestResponse;
import com.vibedev.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock SearchService searchService;

    SearchController controller;

    @BeforeEach
    void setUp() {
        controller = new SearchController(searchService);
    }

    @Test
    void suggest_shouldReturnSuggestions() {
        when(searchService.getSuggest("React"))
                .thenReturn(new SearchSuggestResponse(List.of("React 18 新特性", "React Hooks")));

        var result = controller.suggest("React");

        assertEquals(0, result.code());
        assertEquals(2, result.data().suggestions().size());
        assertTrue(result.data().suggestions().contains("React 18 新特性"));
        verify(searchService).getSuggest("React");
    }

    @Test
    void suggest_shouldReturnEmptyForNoMatches() {
        when(searchService.getSuggest("xyz"))
                .thenReturn(new SearchSuggestResponse(List.of()));

        var result = controller.suggest("xyz");

        assertEquals(0, result.code());
        assertTrue(result.data().suggestions().isEmpty());
    }
}
