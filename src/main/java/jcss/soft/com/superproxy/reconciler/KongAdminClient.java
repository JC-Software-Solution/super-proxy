package jcss.soft.com.superproxy.reconciler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin façade over the Kong Admin API used by {@link SuperProxyReconciler}.
 *
 * <p>Operations:
 * <ul>
 *   <li>{@link #upsertRoute} – create or update a Kong route for the given endpoint.</li>
 *   <li>{@link #enablePluginOnRoute} – attach a named plugin to a route.</li>
 *   <li>{@link #deleteRoute} – remove a route by name (called during finalizer cleanup).</li>
 * </ul>
 *
 * <p>Uses Spring's RestClient for HTTP communication with automatic JSON serialization.
 */
@Slf4j
@Service
public class KongAdminClient {

    @Value("${operator.kong.admin-url}")
    String kongAdminUrl;

    private final RestClient restClient;

    public KongAdminClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    // ── Route ──────────────────────────────────────────────────────────────────

    /**
     * Creates a Kong route if one with {@code routeName} does not exist yet,
     * or patches it in place if it does.
     *
     * @return the Kong route ID (UUID string)
     */
    public String upsertRoute(String routeName,
                              String endpoint,
                              List<String> methods,
                              String upstreamHost,
                              String kongServiceName,
                              boolean stripPath,
                              boolean preserveHost,
                              List<String> tags) {

        try {
            // Build route payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", routeName);
            payload.put("paths", List.of(endpoint));
            payload.put("methods", methods != null ? methods : List.of());
            payload.put("strip_path", stripPath);
            payload.put("preserve_host", preserveHost);
            
            if (tags != null && !tags.isEmpty()) {
                payload.put("tags", tags);
            }

            // Attach to Kong service
            payload.put("service", Map.of("name", kongServiceName));

            String putUrl = kongAdminUrl + "/routes/" + routeName;
            log.debug("PUT {} → {}", putUrl, payload);

            Map<String, Object> result;
            try {
                result = restClient.put()
                        .uri(putUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(Map.class);
            } catch (Exception e) {
                // Route does not exist – create via POST
                log.debug("Route {} not found, creating via POST", routeName);
                result = restClient.post()
                        .uri(kongAdminUrl + "/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .body(Map.class);
            }

            if (result == null || !result.containsKey("id")) {
                throw new RuntimeException("Kong route upsert failed: invalid response");
            }

            String routeId = result.get("id").toString();
            log.info("Kong route {} (id={}) upserted successfully", routeName, routeId);
            return routeId;

        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert Kong route: " + e.getMessage(), e);
        }
    }

    // ── Plugin ─────────────────────────────────────────────────────────────────

    /**
     * Attaches a named plugin to an existing Kong route.
     * Idempotent: if the plugin is already attached the call is a no-op.
     */
    public void enablePluginOnRoute(String routeId, String pluginName) {
        try {
            Map<String, Object> payload = Map.of("name", pluginName);

            String url = kongAdminUrl + "/routes/" + routeId + "/plugins";
            log.debug("POST {} → {}", url, payload);

            restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            log.info("Plugin {} enabled on route {}", pluginName, routeId);
        } catch (Exception e) {
            // 409 means already enabled – that is fine
            if (e.getMessage() != null && e.getMessage().contains("409")) {
                log.info("Plugin {} already enabled on route {}", pluginName, routeId);
            } else {
                throw new RuntimeException("Failed to enable Kong plugin: " + e.getMessage(), e);
            }
        }
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    /**
     * Deletes a Kong route by name.  Called during finalizer cleanup.
     * A 404 is treated as success (already gone).
     */
    public void deleteRoute(String routeName) {
        try {
            String url = kongAdminUrl + "/routes/" + routeName;
            log.debug("DELETE {}", url);

            restClient.delete()
                    .uri(url)
                    .retrieve()
                    .body(Void.class);

            log.info("Kong route {} deleted", routeName);
        } catch (Exception e) {
            // 404 is acceptable (already gone)
            if (e.getMessage() != null && e.getMessage().contains("404")) {
                log.info("Kong route {} already deleted", routeName);
            } else {
                throw new RuntimeException("Kong route deletion failed: " + e.getMessage(), e);
            }
        }
    }
}
