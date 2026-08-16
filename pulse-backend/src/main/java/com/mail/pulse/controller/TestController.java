package com.mail.pulse.controller;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/test")
public class TestController {
    private String customStatus = "Initial Status";


    @PostMapping("/ping")
    public Map<String, Object> updatePing(@RequestBody Map<String, String> payload) {

        // Extract value sent from React
        String newStatus = payload.get("status");


        if (newStatus != null && !newStatus.isBlank()) {
            this.customStatus = newStatus;
        }

        return Map.of(
                "message", "Status updated successfully!",
                "currentStatus", this.customStatus
        );
    }

    // Request payload matching { "name": "..." }
    public static class NameChangeRequest {
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    // Response payload matching { "name": "..." }
    public static class NameChangeResponse {
        private String name;

        public NameChangeResponse(String name) { this.name = name; }
        public String getName() { return name; }
    }

    @PostMapping("/changeName")
    public NameChangeResponse changeName(@RequestBody NameChangeRequest request) {
        // Process/sanitize the input here as needed
        String updatedName = request.getName();

        return new NameChangeResponse(updatedName);
    }


}