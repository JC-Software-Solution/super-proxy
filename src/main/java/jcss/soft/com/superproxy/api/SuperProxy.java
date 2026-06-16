package jcss.soft.com.superproxy.api;


import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;
import jcss.soft.com.superproxy.spec.SuperProxySpec;
import jcss.soft.com.superproxy.status.SuperProxyStatus;

/**
 * Kubernetes Custom Resource: {@code SuperProxy}.
 *
 * <p>A {@code SuperProxy} resource lets an application team expose an internal
 * Kubernetes service outside the cluster without manually configuring ingress,
 * Kong routes, or proxy services.  The operator detects this resource and
 * automatically provisions:
 *
 * <ul>
 *   <li>An {@code authorize_proxy} Deployment</li>
 *   <li>A ClusterIP Service in front of the proxy pods</li>
 *   <li>A Kong route on the configured endpoint</li>
 *   <li>Kong plugin bindings (e.g. IP-allowlist, rate-limiting)</li>
 * </ul>
 *
 * <p>API group: {@code jcss.soft.com / v1}
 */
@Group("jcss.soft.com")
@Version("v1")
@Kind("SuperProxy")
@Plural("superproxies")
@ShortNames("sp")
public class SuperProxy
        extends CustomResource<SuperProxySpec, SuperProxyStatus>
        implements Namespaced {
    // Fabric8 / JOSDK reflection handles spec & status via generics.
    // No additional methods are required here; business logic lives in the reconciler.
}