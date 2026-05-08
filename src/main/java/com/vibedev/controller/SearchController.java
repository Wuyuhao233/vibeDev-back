package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.dto.search.SearchResponse;
import com.vibedev.dto.search.SearchSuggestResponse;
import com.vibedev.dto.search.SearchSuggestionResponse;
import com.vibedev.search.SearchQuery;
import com.vibedev.search.SearchScope;
import com.vibedev.service.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ApiResponse<SearchResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(required = false) String board_id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {

        String trimmedQ = q.trim();
        SearchQuery query = new SearchQuery(trimmedQ, SearchScope.from(scope),
                board_id, page, Math.min(limit, 50));

        String ipAddress = getClientIp(request);
        String userId = null; // Search is public, no auth required

        SearchResponse result = searchService.search(query, ipAddress, userId);
        return ApiResponse.ok(result);
    }

    @GetMapping("/suggestions")
    public ApiResponse<SearchSuggestionResponse> suggestions() {
        return ApiResponse.ok(searchService.getSuggestions());
    }

    @GetMapping("/suggest")
    public ApiResponse<SearchSuggestResponse> suggest(@RequestParam String q) {
        return ApiResponse.ok(searchService.getSuggest(q));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
