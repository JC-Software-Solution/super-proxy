package jcss.soft.com.superproxy.status;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Observed state written back to the {@code SuperProxy} resource by the operator.
 * All fields are optional; the operator populates them progressively as child
 * resources are created and become healthy.
 */
@Data
@Builder
public class SuperProxyStatus {

    /**
     * High-level lifecycle phase of this SuperProxy.
     */
    public enum Phase {
        Pending,
        Provisioning,
        Ready,
        Degraded,
        Error
    }

    /**
     * Human-readable readiness summary, e.g. {@code "1/1"} or {@code "Pending"}.
     */
    private String ready;

    /**
     * High-level lifecycle phase.
     */
    private Phase phase;

    /**
     * Human-readable message explaining the current phase.
     * Particularly useful when {@code phase} is {@code Error} or {@code Degraded}.
     */
    private String message;

    /**
     * The {@code metadata.generation} this status was derived from.
     * Used by clients to detect stale status fields.
     */
    private Long observedGeneration;

    /**
     * Name of the Deployment created for the authorize_proxy pods.
     */
    private String deploymentName;

    /**
     * Name of the Service created to front the proxy pods.
     */
    private String serviceName;

    /**
     * Kong route ID assigned after successful route creation via the Admin API.
     */
    private String kongRouteId;

    /**
     * Fine-grained condition list following the standard Kubernetes Condition pattern.
     */
    private List<ProxyCondition> conditions;

    // ── Condition helper ───────────────────────────────────────────────────────

    @Data
    @Builder
    public static class ProxyCondition {

        /** Condition type, e.g. {@code DeploymentReady}, {@code KongRouteReady}. */
        private String type;

        /** {@code True}, {@code False}, or {@code Unknown}. */
        private String status;

        /** Machine-readable reason token. */
        private String reason;

        /** Human-readable message. */
        private String message;

        /** RFC 3339 timestamp of last transition. */
        private String lastTransitionTime;

        /** Generation observed when this condition was last updated. */
        private Long observedGeneration;
    }
}
