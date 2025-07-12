package com.infrastructure.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "function_metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class FunctionMetricsEntity {
    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "function_id", nullable = false)
    private String functionId;

    @Column(name = "function_name")
    private String functionName;

    @Column(name = "app_name")
    private String appName;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "invocation_count")
    @Builder.Default
    private long invocationCount = 0;

    @Column(name = "success_count")
    @Builder.Default
    private long successCount = 0;

    @Column(name = "failure_count")
    @Builder.Default
    private long failureCount = 0;

    @Column(name = "total_execution_time_ms")
    @Builder.Default
    private long totalExecutionTimeMs = 0;

    @Column(name = "min_execution_time_ms")
    @Builder.Default
    private long minExecutionTimeMs = Long.MAX_VALUE;

    @Column(name = "max_execution_time_ms")
    @Builder.Default
    private long maxExecutionTimeMs = 0;

    @Column(name = "last_invoked")
    private LocalDateTime lastInvoked;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}