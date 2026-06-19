package jcss.soft.com.superproxy.spec;


import com.fasterxml.jackson.annotation.JsonProperty;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import lombok.Data;

import java.util.List;

/**
 * Desired state declared by an application team when creating a {@code SuperProxy} resource.
 *
 * <p>The operator reads this spec and reconciles child resources (Deployment, Service,
 * Kong route) to match.
 */
@Data
public class SuperProxySpec {

    /**
     * Deployment environment, e.g. {@code uat}, {@code staging}, {@code prod}.
     * Used as a prefix in Kong endpoint paths and to label child resources.
     */
    private String env;

    /**
     * Logical name for the proxy.  Child resources are named {@code <name>-proxy}.
     */
    private String name;

    /**
     * Optional semantic version label for the proxied API (informational only).
     */
    private String version;

    /**
     * Full image reference for the {@code authorize_proxy} container including tag.
     * Example: {@code artifacts-internal.mynt.xyz/mtc/cluster4/authorize_proxy:v3.7.1}
     */
    private String image;

    /**
     * Full URL of the internal Kubernetes service that this proxy fronts.
     * Must include scheme and port, e.g. {@code http://my-svc.namespace:8080}.
     */
    private String origin;

    /**
     * Number of authorize_proxy pod replicas. Defaults to {@code 1}.
     */
    @JsonProperty("replicas")
    private Integer replicas = 1;

    /**
     * Kong plugin names to attach to the generated route.
     * Each entry must correspond to an existing Kong plugin object.
     * Example: {@code ["ipAddress", "rate-limiting"]}
     */
    private List<SuperProxyPlugin> plugins;

    /**
     * Kong route configuration for the external endpoint.
     */
    private KongConfig kongConfig;

    /**
     * Optional compute resource requests/limits for the proxy container.
     * Uses the standard Kubernetes {@link ResourceRequirements} schema.
     */
    private ResourceRequirements resources;

    /**
     * Additional environment variables injected into the authorize_proxy container.
     */
    private List<ProxyEnvVar> extraEnv;
}