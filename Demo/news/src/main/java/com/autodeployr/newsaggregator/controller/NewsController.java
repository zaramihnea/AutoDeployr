package com.autodeployr.newsaggregator.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import java.net.URL;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class NewsController {

    @PostMapping("/feeds")
    public ResponseEntity<Map<String, Object>> getFeeds(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> urls = (List<String>) request.get("urls");
            
            if (urls == null || urls.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "URLs list is required and cannot be empty");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            List<Map<String, Object>> allArticles = new ArrayList<>();
            
            for (String urlString : urls) {
                try {
                    URL url = new URL(urlString);
                    SyndFeedInput input = new SyndFeedInput();
                    SyndFeed feed = input.build(new XmlReader(url));
                    
                    for (SyndEntry entry : feed.getEntries()) {
                        Map<String, Object> article = new HashMap<>();
                        article.put("title", entry.getTitle());
                        article.put("description", entry.getDescription() != null ? entry.getDescription().getValue() : "");
                        article.put("link", entry.getLink());
                        article.put("publishedDate", entry.getPublishedDate());
                        article.put("source", feed.getTitle());
                        allArticles.add(article);
                    }
                } catch (Exception e) {
                    // Log error but continue with other URLs
                    System.err.println("Error processing URL " + urlString + ": " + e.getMessage());
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("articles", allArticles);
            response.put("totalCount", allArticles.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Internal server error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> searchNews(@RequestBody Map<String, Object> request) {
        try {
            String searchQuery = (String) request.get("query");
            
            if (searchQuery == null || searchQuery.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Search query is required and cannot be empty");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // Hardcoded tech news sources
            List<String> techSources = Arrays.asList(
                "https://9to5mac.com/feed/",
                "https://9to5google.com/feed/",
                "https://techcrunch.com/feed/"
            );

            List<Map<String, Object>> matchingArticles = new ArrayList<>();
            String lowerQuery = searchQuery.toLowerCase();
            
            for (String urlString : techSources) {
                try {
                    URL url = new URL(urlString);
                    SyndFeedInput input = new SyndFeedInput();
                    SyndFeed feed = input.build(new XmlReader(url));
                    
                    for (SyndEntry entry : feed.getEntries()) {
                        String title = entry.getTitle() != null ? entry.getTitle().toLowerCase() : "";
                        String description = "";
                        if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
                            description = entry.getDescription().getValue().toLowerCase();
                        }
                        
                        // Check if query appears in title or description
                        if (title.contains(lowerQuery) || description.contains(lowerQuery)) {
                            Map<String, Object> article = new HashMap<>();
                            article.put("title", entry.getTitle());
                            article.put("description", entry.getDescription() != null ? entry.getDescription().getValue() : "");
                            article.put("link", entry.getLink());
                            article.put("publishedDate", entry.getPublishedDate());
                            article.put("source", feed.getTitle());
                            matchingArticles.add(article);
                        }
                    }
                } catch (Exception e) {
                    // Log error but continue with other URLs
                    System.err.println("Error processing URL " + urlString + ": " + e.getMessage());
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("articles", matchingArticles);
            response.put("totalCount", matchingArticles.size());
            response.put("query", searchQuery);
            response.put("sources", Arrays.asList("9to5Mac", "9to5Google", "TechCrunch"));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Internal server error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
} 