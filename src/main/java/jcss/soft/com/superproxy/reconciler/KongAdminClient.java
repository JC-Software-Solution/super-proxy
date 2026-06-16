package jcss.soft.com.superproxy.reconciler;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import jakarta.ws.rs.client.Client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

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
 * <p>In a real implementation you would use the Quarkus REST client with a typed
 * interface; a plain JAX-RS {@link Client} is used here to keep the example
 * self-contained and dependency-free.
 */
@Slf4j
@Service
public class KongAdminClient {

    @Value("${operator.kong.admin-url:http://kong-admin.kong:8001}")
    String kongAdminUrl;

    @Autowired
    ObjectMapper mapper;

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

        Client client = ClientBuilder.newClient();
        try {
            // Build route payload
            ObjectNode payload = mapper.createObjectNode();
            payload.put("name", routeName);

            ArrayNode pathsNode = mapper.createArrayNode();
            pathsNode.add(endpoint);
            payload.set("paths", pathsNode);

            ArrayNode methodsNode = mapper.createArrayNode();
            if (methods != null) methods.forEach(methodsNode::add);
            payload.set("methods", methodsNode);

            payload.put("strip_path",    stripPath);
            payload.put("preserve_host", preserveHost);

            if (tags != null && !tags.isEmpty()) {
                ArrayNode tagsNode = mapper.createArrayNode();
                tags.forEach(tagsNode::add);
                payload.set("tags", tagsNode);
            }

            // Attach to Kong service
            ObjectNode serviceNode = mapper.createObjectNode();
            serviceNode.put("name", kongServiceName);
            payload.set("service", serviceNode);

            String url = kongAdminUrl + "/routes/" + routeName;
            log.debug("PUT {} → {}", url, payload);

            Response response = client.target(url)
                    .request(MediaType.APPLICATION_JSON)
                    .put(Entity.json(payload.toString()));

            if (response.getStatus() == 404) {
                // Route does not exist – create via POST
                response = client.target(kongAdminUrl + "/routes")
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(payload.toString()));
            }

            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new RuntimeException("Kong route upsert failed: HTTP " + response.getStatus()
                        + " – " + response.readEntity(String.class));
            }

            JsonNode result = mapper.readTree(response.readEntity(String.class));
            String routeId = result.get("id").asText();
            log.info("Kong route {} (id={}) upserted successfully", routeName, routeId);
            return routeId;

        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert Kong route: " + e.getMessage(), e);
        } finally {
            client.close();
        }
    }

    // ── Plugin ─────────────────────────────────────────────────────────────────

    /**
     * Attaches a named plugin to an existing Kong route.
     * Idempotent: if the plugin is already attached the call is a no-op.
     */
    public void enablePluginOnRoute(String routeId, String pluginName) {
        Client client = ClientBuilder.newClient();
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("name", pluginName);

            String url = kongAdminUrl + "/routes/" + routeId + "/plugins";
            log.debug("POST {} → {}", url, payload);

            Response response = client.target(url)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(payload.toString()));

            // 409 means already enabled – that is fine
            if (response.getStatus() != 409
                    && (response.getStatus() < 200 || response.getStatus() >= 300)) {
                throw new RuntimeException("Failed to enable plugin " + pluginName
                        + " on route " + routeId + ": HTTP " + response.getStatus());
            }

            log.info("Plugin {} enabled on route {}", pluginName, routeId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to enable Kong plugin: " + e.getMessage(), e);
        } finally {
            client.close();
        }
    }

    // ── Delete ─────────────────────────────────────────────────────────────────

    /**
     * Deletes a Kong route by name.  Called during finalizer cleanup.
     * A 404 is treated as success (already gone).
     */
    public void deleteRoute(String routeName) {
        Client client = ClientBuilder.newClient();
        try {
            String url = kongAdminUrl + "/routes/" + routeName;
            log.debug("DELETE {}", url);

            Response response = client.target(url)
                    .request()
                    .delete();

            if (response.getStatus() != 204 && response.getStatus() != 404) {
                throw new RuntimeException("Kong route deletion failed: HTTP " + response.getStatus());
            }

            log.info("Kong route {} deleted", routeName);
        } finally {
            client.close();
        }
    }
}
