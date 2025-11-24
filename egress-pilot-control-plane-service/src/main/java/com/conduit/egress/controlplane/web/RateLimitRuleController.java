package com.conduit.egress.controlplane.web;

import com.conduit.egress.controlplane.model.RateLimitRuleEntity;
import com.conduit.egress.controlplane.repo.RateLimitRuleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rules")
public class RateLimitRuleController {

    private final RateLimitRuleRepository repository;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public RateLimitRuleController(
            RateLimitRuleRepository repository,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public List<RateLimitRuleDto> list(@RequestParam("service") String serviceName) {
        meterRegistry.counter("control_plane.rule.list").increment();
        return repository.findByServiceName(serviceName)
                .stream()
                .map(RateLimitRuleMapper::toDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/all")
    public List<RateLimitRuleDto> listAll() {
        meterRegistry.counter("control_plane.rule.list_all").increment();
        return repository.findAll()
                .stream()
                .map(RateLimitRuleMapper::toDto)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<RateLimitRuleDto> create(@Valid @RequestBody RateLimitRuleDto dto) {
        if (repository.existsByServiceNameAndName(dto.getServiceName(), dto.getName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        RateLimitRuleEntity entity = RateLimitRuleMapper.toEntity(dto);
        try {
            RateLimitRuleEntity saved = repository.save(entity);
            RateLimitRuleDto body = RateLimitRuleMapper.toDto(saved);
            meterRegistry.counter("control_plane.rule.create").increment();
            return ResponseEntity.created(URI.create("/api/v1/rules/" + saved.getId()))
                    .body(body);
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<RateLimitRuleDto> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody RateLimitRuleDto dto
    ) {
        return repository.findById(id)
                .map(existing -> {
                    return repository.findByServiceNameAndName(dto.getServiceName(), dto.getName())
                            .filter(conflict -> !conflict.getId().equals(id))
                            .map(conflict -> ResponseEntity.status(HttpStatus.CONFLICT).<RateLimitRuleDto>build())
                            .orElseGet(() -> {
                                RateLimitRuleEntity updated = RateLimitRuleMapper.toEntity(dto);
                                updated.setId(id);
                                try {
                                    RateLimitRuleEntity saved = repository.save(updated);
                                    meterRegistry.counter("control_plane.rule.update").increment();
                                    return ResponseEntity.ok(RateLimitRuleMapper.toDto(saved));
                                } catch (DataIntegrityViolationException ex) {
                                    return ResponseEntity.status(HttpStatus.CONFLICT).<RateLimitRuleDto>build();
                                }
                            });
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        meterRegistry.counter("control_plane.rule.delete").increment();
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/bulk", consumes = "multipart/form-data")
    public ResponseEntity<BulkImportResult> bulkImport(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<RateLimitRuleDto> created = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        try {
            List<RateLimitRuleDto> incoming = objectMapper.readValue(
                    file.getInputStream(),
                    new TypeReference<List<RateLimitRuleDto>>() {
                    }
            );

            for (RateLimitRuleDto dto : incoming) {
                if (repository.existsByServiceNameAndName(dto.getServiceName(), dto.getName())) {
                    errors.add("Duplicate rule for service=" + dto.getServiceName() + " name=" + dto.getName());
                    continue;
                }
                try {
                    RateLimitRuleEntity saved = repository.save(RateLimitRuleMapper.toEntity(dto));
                    created.add(RateLimitRuleMapper.toDto(saved));
                } catch (DataIntegrityViolationException ex) {
                    errors.add("Constraint violation for service=" + dto.getServiceName() + " name=" + dto.getName());
                }
            }
            if (!created.isEmpty()) {
                meterRegistry.counter("control_plane.rule.bulk_create").increment(created.size());
            }
            BulkImportResult result = new BulkImportResult(created, errors);
            return errors.isEmpty() ? ResponseEntity.status(HttpStatus.CREATED).body(result)
                    : ResponseEntity.status(HttpStatus.MULTI_STATUS).body(result);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new BulkImportResult(List.of(), List.of("Failed to parse JSON file: " + ex.getMessage()))
            );
        }
    }

    public record BulkImportResult(List<RateLimitRuleDto> created, List<String> errors) {
    }
}
