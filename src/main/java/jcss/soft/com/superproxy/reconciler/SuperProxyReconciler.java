package jcss.soft.com.superproxy.reconciler;


import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;

import jcss.soft.com.superproxy.spec.SuperProxySpec;
import jcss.soft.com.superproxy.status.SuperProxyStatus;
import jcss.soft.com.superproxy.api.SuperProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Core reconciliation loop for {@link SuperProxy} resources.
 *
 * <p>On every reconcile cycle the operator:
 * <ol>
 *   <li>Creates or updates the {@code authorize_proxy} <b>Deployment</b>.</li>
 *   <li>Creates or updates the ClusterIP <b>Service</b> in front of the pods.</li>
 *   <li>Calls the Kong Admin API to create or patch the <b>Kong route</b>
 *       on the requested endpoint.</li>
 *   <li>Attaches the requested <b>Kong plugins</b> to the route.</li>
 *   <li>Writes the observed state back to {@code .status}.</li>
 * </ol>
 *
 * <p>Owner references are set on every child resource so they are garbage-collected
 * when the {@code SuperProxy} is deleted.
 */
@Slf4j
@Component
@ControllerConfiguration(
        name = "superproxy-controller",
        finalizerName = "jcss.soft.com/finalizer"
)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SuperProxyReconciler implements Reconciler<SuperProxy>, Cleaner<SuperProxy> {

    // ── Port constants ──────────────────────────────────────────────────────────
    private static final int PROXY_CONTAINER_PORT = 8080;
    private static final int SERVICE_PORT          = 80;
    private static final String APP_LABEL          = "app.kubernetes.io/managed-by";
    private static final String MANAGED_BY_VALUE   = "superproxy-operator";

    // ── Dependencies ────────────────────────────────────────────────────────────
    private final KubernetesClient k8s;
    private final KongAdminClient  kongAdmin;

    // ── Config ──────────────────────────────────────────────────────────────────
    @Value("${operator.kong.admin-url:http://kong-admin.kong:8001}")
    String kongServiceName;

    // ═══════════════════════════════════════════════════════════════════════════
    // reconcile
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public UpdateControl<SuperProxy> reconcile(SuperProxy resource, Context<SuperProxy> context) {
        SuperProxySpec spec = resource.getSpec();
        String         ns   = resource.getMetadata().getNamespace();
        String         name = proxyName(spec);

        log.info("Reconciling SuperProxy {}/{}", ns, resource.getMetadata().getName());

        List<SuperProxyStatus.ProxyCondition> conditions = new ArrayList<>();

        try {
            // 1. Deployment ──────────────────────────────────────────────────────
            Deployment deployment = buildDeployment(resource, name, ns);
            applyDeployment(deployment, ns);
            conditions.add(condition("DeploymentReady", "True", "DeploymentApplied",
                    "Deployment " + name + " created/updated"));

            // 2. Service ─────────────────────────────────────────────────────────
            Service service = buildService(resource, name, ns);
            applyService(service, ns);
            conditions.add(condition("ServiceReady", "True", "ServiceApplied",
                    "Service " + name + " created/updated"));

            // 3. Kong route ──────────────────────────────────────────────────────
            String kongRouteId = kongAdmin.upsertRoute(
                    name,
                    spec.getKongConfig().getEndpoint(),
                    spec.getKongConfig().getMethods(),
                    serviceFqdn(name, ns),
                    kongServiceName,
                    Boolean.TRUE.equals(spec.getKongConfig().getStripPath()),
                    Boolean.TRUE.equals(spec.getKongConfig().getPreserveHost()),
                    spec.getKongConfig().getTags()
            );
            conditions.add(condition("KongRouteReady", "True", "RouteUpserted",
                    "Kong route " + kongRouteId + " active"));

            // 4. Kong plugins ────────────────────────────────────────────────────
            if (spec.getPlugins() != null) {
                for (String plugin : spec.getPlugins()) {
                    kongAdmin.enablePluginOnRoute(kongRouteId, plugin);
                }
            }
            conditions.add(condition("PluginsReady", "True", "PluginsAttached",
                    "All plugins attached to route " + kongRouteId));

            // 5. Status ──────────────────────────────────────────────────────────
            SuperProxyStatus status = SuperProxyStatus.builder()
                    .phase(SuperProxyStatus.Phase.Ready)
                    .ready(spec.getReplicas() + "/" + spec.getReplicas())
                    .message("SuperProxy is fully provisioned")
                    .observedGeneration(resource.getMetadata().getGeneration())
                    .deploymentName(name)
                    .serviceName(name)
                    .kongRouteId(kongRouteId)
                    .conditions(conditions)
                    .build();

            resource.setStatus(status);
            return UpdateControl.updateStatus(resource);

        } catch (Exception e) {
            log.error("Failed to reconcile SuperProxy {}/{}: {}", ns,
                    resource.getMetadata().getName(), e.getMessage(), e);

            conditions.add(condition("ReconcileError", "True", "ReconcileFailed", e.getMessage()));

            SuperProxyStatus errorStatus = SuperProxyStatus.builder()
                    .phase(SuperProxyStatus.Phase.Error)
                    .ready("Error")
                    .message(e.getMessage())
                    .observedGeneration(resource.getMetadata().getGeneration())
                    .conditions(conditions)
                    .build();

            resource.setStatus(errorStatus);
            return UpdateControl.updateStatus(resource).rescheduleAfter(30_000);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // cleanup (finalizer)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public DeleteControl cleanup(SuperProxy resource, Context<SuperProxy> context) {
        SuperProxySpec spec = resource.getSpec();
        String         name = proxyName(spec);
        log.info("Cleaning up SuperProxy {}/{}", resource.getMetadata().getNamespace(), name);

        try {
            kongAdmin.deleteRoute(name);
        } catch (Exception e) {
            log.warn("Could not delete Kong route for {}: {}", name, e.getMessage());
        }

        // Child k8s resources are garbage-collected via ownerReferences — no
        // explicit deletion is required here.
        return DeleteControl.defaultDelete();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Deployment helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private Deployment buildDeployment(SuperProxy cr, String name, String ns) {
        SuperProxySpec spec = cr.getSpec();

        // Base env: pass the upstream origin URL to the proxy container
        List<EnvVar> envVars = new ArrayList<>();
        envVars.add(new EnvVarBuilder()
                .withName("ORIGIN_URL")
                .withValue(spec.getOrigin())
                .build());

        // Extra env vars from spec
        if (spec.getExtraEnv() != null) {
            spec.getExtraEnv().forEach(e -> envVars.add(
                    new EnvVarBuilder().withName(e.getName()).withValue(e.getValue()).build()));
        }

        Container container = new ContainerBuilder()
                .withName("authorize-proxy")
                .withImage(spec.getImage())
                .withPorts(new ContainerPortBuilder()
                        .withContainerPort(PROXY_CONTAINER_PORT)
                        .withProtocol("TCP")
                        .build())
                .withEnv(envVars)
                .withResources(spec.getResources())
                .withReadinessProbe(new ProbeBuilder()
                        .withHttpGet(new HTTPGetActionBuilder()
                                .withPath("/health/ready")
                                .withPort(new IntOrString(PROXY_CONTAINER_PORT))
                                .build())
                        .withInitialDelaySeconds(5)
                        .withPeriodSeconds(10)
                        .build())
                .withLivenessProbe(new ProbeBuilder()
                        .withHttpGet(new HTTPGetActionBuilder()
                                .withPath("/health/live")
                                .withPort(new IntOrString(PROXY_CONTAINER_PORT))
                                .build())
                        .withInitialDelaySeconds(15)
                        .withPeriodSeconds(20)
                        .build())
                .build();

        Map<String, String> labels = proxyLabels(name, spec);

        return new DeploymentBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(ns)
                .withLabels(labels)
                .withOwnerReferences(ownerRef(cr))
                .endMetadata()
                .withNewSpec()
                .withReplicas(spec.getReplicas())
                .withNewSelector()
                .addToMatchLabels("app", name)
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withContainers(container)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    private void applyDeployment(Deployment desired, String ns) {
        k8s.apps().deployments().inNamespace(ns)
                .resource(desired)
                .serverSideApply();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Service helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private Service buildService(SuperProxy cr, String name, String ns) {
        Map<String, String> selector = Map.of("app", name);

        return new ServiceBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(ns)
                .withLabels(proxyLabels(name, cr.getSpec()))
                .withOwnerReferences(ownerRef(cr))
                .endMetadata()
                .withNewSpec()
                .withSelector(selector)
                .withType("ClusterIP")
                .withPorts(new ServicePortBuilder()
                        .withPort(SERVICE_PORT)
                        .withTargetPort(new IntOrString(PROXY_CONTAINER_PORT))
                        .withProtocol("TCP")
                        .build())
                .endSpec()
                .build();
    }

    private void applyService(Service desired, String ns) {
        k8s.services().inNamespace(ns)
                .resource(desired)
                .serverSideApply();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Misc helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Child resource name derived from the spec logical name. */
    private static String proxyName(SuperProxySpec spec) {
        return spec.getName() + "-proxy";
    }

    /** In-cluster DNS name for the proxy Service. */
    private static String serviceFqdn(String name, String ns) {
        return name + "." + ns + ".svc.cluster.local";
    }

    private static Map<String, String> proxyLabels(String name, SuperProxySpec spec) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("app",              name);
        m.put("env",              spec.getEnv());
        m.put(APP_LABEL,          MANAGED_BY_VALUE);
        m.put("jcss.soft.com/name", spec.getName());
        return m;
    }

    private static OwnerReference ownerRef(SuperProxy cr) {
        return new OwnerReferenceBuilder()
                .withApiVersion(cr.getApiVersion())
                .withKind(cr.getKind())
                .withName(cr.getMetadata().getName())
                .withUid(cr.getMetadata().getUid())
                .withController(true)
                .withBlockOwnerDeletion(true)
                .build();
    }

    private static SuperProxyStatus.ProxyCondition condition(String type, String status,
                                                             String reason, String message) {
        return SuperProxyStatus.ProxyCondition.builder()
                .type(type)
                .status(status)
                .reason(reason)
                .message(message)
                .lastTransitionTime(Instant.now().toString())
                .build();
    }

//    @Override
//    public DeleteControl cleanup(SuperProxy superProxy, Context<SuperProxy> context) {
//        return null;
//    }
}