// src/main/java/com/example/ratelimiter/rules/RuleController.java
package com.example.ratelimiter.rules;

import com.example.ratelimiter.rules.dto.RuleRequest;
import com.example.ratelimiter.rules.dto.RuleUpdateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/api/v1/rules")
@Validated
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping
    public ResponseEntity<Rule> create(@RequestBody RuleRequest request) {
        Rule rule = ruleService.create(request.keyPrefix(), request.limit(), request.windowSeconds());
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    @GetMapping
    public Collection<Rule> list() {
        return ruleService.list().values();
    }

    @GetMapping("/{prefix}")
    public Rule get(@PathVariable String prefix) {
        return ruleService.get(prefix).orElseThrow(() -> new RuleNotFoundException(prefix));
    }

    @PutMapping("/{prefix}")
    public Rule update(@PathVariable String prefix, @RequestBody RuleUpdateRequest request) {
        return ruleService.update(prefix, request.limit(), request.windowSeconds());
    }

    @DeleteMapping("/{prefix}")
    public ResponseEntity<Void> delete(@PathVariable String prefix) {
        ruleService.delete(prefix);
        return ResponseEntity.noContent().build();
    }
}
