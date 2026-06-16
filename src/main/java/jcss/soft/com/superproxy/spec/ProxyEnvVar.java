package jcss.soft.com.superproxy.spec;


import lombok.Data;

/**
 * A single extra environment variable injected into the authorize_proxy container.
 * Mirrors the Kubernetes EnvVar schema (plain value or valueFrom).
 */
@Data
public class ProxyEnvVar {

    /** Environment variable name. */
    private String name;

    /** Literal value. Mutually exclusive with {@link #valueFrom}. */
    private String value;

    /**
     * Source reference (ConfigMapKeyRef, SecretKeyRef, FieldRef …).
     * Stored as a raw map so any Kubernetes EnvVarSource variant is accepted.
     */
    private java.util.Map<String, Object> valueFrom;
}
