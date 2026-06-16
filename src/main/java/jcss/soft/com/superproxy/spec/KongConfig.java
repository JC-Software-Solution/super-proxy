package jcss.soft.com.superproxy.spec;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Kong-specific routing configuration for a SuperProxy.
 * The operator uses this to create (or update) a Kong route and
 * attach the requested plugins.
 */
@Data
public class KongConfig {

    /**
     * External path exposed on Kong, e.g. {@code /uat/1.0/zsm/bl-data-enrollment}.
     */
    private String endpoint;

    /**
     * HTTP methods accepted on the route (POST, GET, …).
     */
    private List<String> methods;

    /**
     * When {@code true}, Kong strips the route prefix before forwarding
     * to the upstream proxy service. Defaults to {@code false}.
     */
    @JsonProperty("stripPath")
    private Boolean stripPath = false;

    /**
     * When {@code true}, Kong forwards the original Host header from the
     * client. Defaults to {@code false}.
     */
    @JsonProperty("preserveHost")
    private Boolean preserveHost = false;

    /**
     * Arbitrary string tags attached to the Kong route object.
     */
    private List<String> tags;
}