package jcss.soft.com.superproxy.status;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuperProxyStatus {

    public enum Phase {
        Pending, Provisioning, Ready, Degraded, Error
    }

    private String ready;
    private Phase phase;
    private String message;
    private Long observedGeneration;
    private String deploymentName;
    private String serviceName;
    private String kongRouteId;
    private List<ProxyCondition> conditions;

    @Data
    @Builder
    @NoArgsConstructor   // ← add this
    @AllArgsConstructor  // ← add this
    public static class ProxyCondition {
        private String type;
        private String status;
        private String reason;
        private String message;
        private String lastTransitionTime;
        private Long observedGeneration;
    }
}