package com.medkernel.shared.idempotency;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medkernel.shared.api.ApiResult;
import com.medkernel.shared.context.OrgScope;
import com.medkernel.shared.context.RequestContext;
import com.medkernel.shared.trace.TraceIdFilter;
import jakarta.servlet.Filter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BASE-03 · 平台级 Idempotency-Key 契约测试。
 */
class IdempotencyFilterTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final InMemoryIdempotencyRepository repository = new InMemoryIdempotencyRepository();
    private final AtomicInteger createCalls = new AtomicInteger();

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FixtureController(createCalls))
            .addFilters(
                new TraceIdFilter(),
                tenantContext("tenant-A"),
                new IdempotencyFilter(
                    repository,
                    new IdempotencyProperties(true, Duration.ofHours(24)),
                    mapper))
            .build();
    }

    @Test
    void repeatedWriteWithSameIdempotencyKeyReplaysFirstResultWithoutSecondSideEffect() throws Exception {
        String body = mapper.writeValueAsString(new CreateOrderRequest("order-A"));

        mvc.perform(post("/orders")
                .header(TraceIdFilter.HEADER, "trace-first")
                .header(IdempotencyFilter.HEADER, "idem-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(header().string(TraceIdFilter.HEADER, "trace-first"))
            .andExpect(jsonPath("$.data.sequence").value(1))
            .andExpect(jsonPath("$.traceId").value("trace-first"));

        mvc.perform(post("/orders")
                .header(TraceIdFilter.HEADER, "trace-second")
                .header(IdempotencyFilter.HEADER, "idem-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(header().string(TraceIdFilter.HEADER, "trace-second"))
            .andExpect(header().string(IdempotencyFilter.REPLAY_HEADER, "true"))
            .andExpect(jsonPath("$.data.sequence").value(1))
            .andExpect(jsonPath("$.traceId").value("trace-second"));

        assertThat(createCalls).hasValue(1);
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadIsRejectedBeforeControllerRuns() throws Exception {
        String firstBody = mapper.writeValueAsString(new CreateOrderRequest("order-A"));
        String changedBody = mapper.writeValueAsString(new CreateOrderRequest("order-B"));

        mvc.perform(post("/orders")
                .header(TraceIdFilter.HEADER, "trace-a")
                .header(IdempotencyFilter.HEADER, "idem-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sequence").value(1));

        mvc.perform(post("/orders")
                .header(TraceIdFilter.HEADER, "trace-b")
                .header(IdempotencyFilter.HEADER, "idem-conflict")
                .contentType(MediaType.APPLICATION_JSON)
                .content(changedBody))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("资源冲突"))
            .andExpect(jsonPath("$.code").value("ENG-API-007"))
            .andExpect(jsonPath("$.detail").value("幂等键已被不同请求内容使用"))
            .andExpect(jsonPath("$.traceId").value("trace-b"));

        assertThat(createCalls).hasValue(1);
    }

    @Test
    void writeWithoutIdempotencyKeyKeepsNormalSideEffectSemantics() throws Exception {
        String body = mapper.writeValueAsString(new CreateOrderRequest("order-A"));

        mvc.perform(post("/orders")
                .header(TraceIdFilter.HEADER, "trace-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sequence").value(1));

        mvc.perform(post("/orders")
                .header(TraceIdFilter.HEADER, "trace-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sequence").value(2));

        assertThat(createCalls).hasValue(2);
    }

    @Test
    void repeatedWriteWithPlainTextResponseReplaysOriginalBody() throws Exception {
        mvc.perform(post("/plain-orders")
                .header(TraceIdFilter.HEADER, "trace-plain-1")
                .header(IdempotencyFilter.HEADER, "idem-plain")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(header().string(TraceIdFilter.HEADER, "trace-plain-1"))
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("plain-1"));

        mvc.perform(post("/plain-orders")
                .header(TraceIdFilter.HEADER, "trace-plain-2")
                .header(IdempotencyFilter.HEADER, "idem-plain")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(header().string(TraceIdFilter.HEADER, "trace-plain-2"))
            .andExpect(header().string(IdempotencyFilter.REPLAY_HEADER, "true"))
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo("plain-1"));

        assertThat(createCalls).hasValue(1);
    }

    private Filter tenantContext(String tenantId) {
        return (request, response, chain) -> {
            RequestContext.Snapshot before = RequestContext.snapshot();
            RequestContext.restore(new RequestContext.Snapshot(
                before.traceId(),
                OrgScope.tenant(tenantId),
                "doctor-1"));
            try {
                chain.doFilter(request, response);
            } finally {
                RequestContext.restore(before);
            }
        };
    }

    private static final class InMemoryIdempotencyRepository implements IdempotencyRepository {
        private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

        @Override
        public Optional<IdempotencyRecord> findActive(String tenantId, String idempotencyKey, Instant now) {
            return Optional.ofNullable(records.get(tenantId + ":" + idempotencyKey))
                .filter(record -> record.expiresAt().isAfter(now));
        }

        @Override
        public boolean reserve(IdempotencyRecord record) {
            return records.putIfAbsent(record.tenantId() + ":" + record.idempotencyKey(), record) == null;
        }

        @Override
        public void complete(IdempotencyRecord record) {
            records.put(record.tenantId() + ":" + record.idempotencyKey(), record);
        }

        @Override
        public void delete(String tenantId, String idempotencyKey) {
            records.remove(tenantId + ":" + idempotencyKey);
        }

        @Override
        public void save(IdempotencyRecord record) {
            complete(record);
        }
    }

    @RestController
    static class FixtureController {
        private final AtomicInteger createCalls;

        FixtureController(AtomicInteger createCalls) {
            this.createCalls = createCalls;
        }

        @PostMapping("/orders")
        ApiResult<CreateOrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
            return ApiResult.ok(new CreateOrderResponse(createCalls.incrementAndGet(), request.name()));
        }

        @PostMapping(value = "/plain-orders", produces = MediaType.TEXT_PLAIN_VALUE)
        String createPlain() {
            return "plain-" + createCalls.incrementAndGet();
        }
    }

    record CreateOrderRequest(@NotBlank String name) {
    }

    record CreateOrderResponse(int sequence, String name) {
    }
}
