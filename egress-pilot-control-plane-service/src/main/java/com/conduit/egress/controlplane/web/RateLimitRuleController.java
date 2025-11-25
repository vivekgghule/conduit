package com.conduit.egress.controlplane.web;

import com.conduit.egress.controlplane.model.RateLimitRuleEntity;
import com.conduit.egress.controlplane.repo.RateLimitRuleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rules")
@Validated
public class RateLimitRuleController {

    private final RateLimitRuleRepository repository;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    private static final List<String> ALLOWED_SORT_FIELDS = List.of(
            "id",
            "serviceName",
            "name",
            "httpMethod",
            "capacity",
            "refillTokens",
            "refillPeriodSeconds"
    );

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    public RateLimitRuleController(
            RateLimitRuleRepository repository,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @GetMapping
    public PagedResponse<RateLimitRuleDto> list(
            @RequestParam("service") @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]+$") String serviceName,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(name = "sort", required = false) List<String> sort
    ) {
        meterRegistry.counter("control_plane.rule.list").increment();
        Pageable pageable = createPageRequest(page, size, sort);
        Page<RateLimitRuleEntity> results = repository.findByServiceName(serviceName, pageable);
        return toPagedResponse(results);
    }

    @GetMapping("/all")
    public PagedResponse<RateLimitRuleDto> listAll(
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @RequestParam(name = "sort", required = false) List<String> sort
    ) {
        meterRegistry.counter("control_plane.rule.list_all").increment();
        Pageable pageable = createPageRequest(page, size, sort);
        Page<RateLimitRuleEntity> results = repository.findAll(pageable);
        return toPagedResponse(results);
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
            @PathVariable("id") @Positive Long id,
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
    public ResponseEntity<Void> delete(@PathVariable("id") @Positive Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        meterRegistry.counter("control_plane.rule.delete").increment();
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/bulk", consumes = "multipart/form-data")
    public ResponseEntity<BulkImportResult> bulkImport(@RequestPart("file") @NotNull MultipartFile file) {
        if (file.isEmpty()) {
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

            if (incoming == null) {
                return ResponseEntity.badRequest().body(new BulkImportResult(List.of(), List.of("No rules provided in file")));
            }

            for (RateLimitRuleDto dto : incoming) {
                if (dto == null) {
                    errors.add("Rule entry is empty");
                    continue;
                }
                List<String> validationErrors = validateDto(dto);
                if (!validationErrors.isEmpty()) {
                    errors.add("Validation failed for " + describeRule(dto) + ": " + String.join("; ", validationErrors));
                    continue;
                }
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

    private Pageable createPageRequest(int page, int size, List<String> sortParameters) {
        Sort sort = parseSort(sortParameters);
        return PageRequest.of(page, size, sort);
    }

    private Sort parseSort(List<String> sortParameters) {
        List<String> normalized = normalizeSortParameters(sortParameters);
        if (normalized.isEmpty()) {
            return Sort.by(Sort.Direction.ASC, "id");
        }

        List<Sort.Order> orders = new ArrayList<>();
        for (String raw : normalized) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String[] parts = raw.split(",", 2);
            String property = parts[0].trim();
            if (!ALLOWED_SORT_FIELDS.contains(property)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort property: " + property);
            }
            Sort.Direction direction = Sort.Direction.ASC;
            if (parts.length == 2 && StringUtils.hasText(parts[1])) {
                try {
                    direction = Sort.Direction.fromString(parts[1].trim());
                } catch (IllegalArgumentException ex) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort direction for property: " + property);
                }
            }
            orders.add(new Sort.Order(direction, property));
        }

        return orders.isEmpty() ? Sort.by(Sort.Direction.ASC, "id") : Sort.by(orders);
    }

    private PagedResponse<RateLimitRuleDto> toPagedResponse(Page<RateLimitRuleEntity> page) {
        List<RateLimitRuleDto> items = page.getContent()
                .stream()
                .map(RateLimitRuleMapper::toDto)
                .toList();
        List<String> sort = page.getSort().isSorted()
                ? page.getSort().stream()
                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase())
                .toList()
                : List.of();
        return new PagedResponse<>(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                sort
        );
    }

    private List<String> validateDto(RateLimitRuleDto dto) {
        Set<ConstraintViolation<RateLimitRuleDto>> violations = validator.validate(dto);
        return violations.stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .toList();
    }

    private String describeRule(RateLimitRuleDto dto) {
        String service = StringUtils.hasText(dto.getServiceName()) ? dto.getServiceName() : "<missing-service>";
        String name = StringUtils.hasText(dto.getName()) ? dto.getName() : "<missing-name>";
        return "service=" + service + " name=" + name;
    }

    private List<String> normalizeSortParameters(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String current = raw.get(i);
            if (!StringUtils.hasText(current)) {
                continue;
            }
            if (!current.contains(",") && i + 1 < raw.size() && !raw.get(i + 1).contains(",")) {
                normalized.add(current + "," + raw.get(i + 1));
                i++;
            } else {
                normalized.add(current);
            }
        }
        return normalized;
    }
}
