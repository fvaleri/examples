package it.fvaleri.example;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apiextensions.v1.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRole;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.strimzi.api.kafka.Crds;
import io.strimzi.api.kafka.model.common.ContainerEnvVarBuilder;
import io.strimzi.api.kafka.model.kafka.Kafka;
import io.strimzi.api.kafka.model.kafka.KafkaBuilder;
import io.strimzi.api.kafka.model.kafka.entityoperator.EntityOperatorTemplateBuilder;
import io.strimzi.api.kafka.model.kafka.listener.GenericKafkaListenerBuilder;
import io.strimzi.api.kafka.model.kafka.listener.KafkaListenerType;
import io.strimzi.api.kafka.model.topic.KafkaTopic;
import io.strimzi.api.kafka.model.topic.KafkaTopicBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.time.Duration.ofMinutes;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Utils {
    private static final Logger LOG = LoggerFactory.getLogger(Utils.class);
    
    private static final String STRIMZI_VERSION = "0.41.0";
    private static final int TO_RECONCILIATION_INTERVAL_SEC = 10;
    private static final int TO_MAX_QUEUE_SIZE = Integer.MAX_VALUE;
    private static final int TO_MAX_BATCH_SIZE = 100;
    private static final long TO_MAX_BATCH_LINGER_MS = 100;

    private Utils() {
    }

    public static void sleepFor(long millis) {
        try {
            MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
        }
    }

    // executor services create non-daemon threads by default, which prevent JVM shutdown
    public static void stopExecutor(ExecutorService executor, long timeoutMs) {
        if (executor == null || timeoutMs < 0) {
            return;
        }
        try {
            executor.shutdown();
            executor.awaitTermination(timeoutMs, MILLISECONDS);
        } catch (InterruptedException e) {
            if (!executor.isTerminated()) {
                executor.shutdownNow();
            }
        }
    }

    private static List<HasMetadata> loadStrimziResources(KubernetesClient client) throws IOException {
        String strimziUrl = format("https://github.com/strimzi/strimzi-kafka-operator/releases/download/%s/strimzi-cluster-operator-%s.yaml", STRIMZI_VERSION, STRIMZI_VERSION);
        List<HasMetadata> resources = client.load(new BufferedInputStream(new URL(strimziUrl).openStream())).items();
        return resources;
    }

    public static void createNamespace(KubernetesClient client, String name) {
        if (client.namespaces().withName(name).get() == null) {
            LOG.debug("Creating Namespace {}", name);
            client.namespaces().resource(new NamespaceBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .build()).createOrReplace();
            waitForNamespaceReady(client, name);
        }
    }

    public static void deleteNamespace(KubernetesClient client, String name) {
        LOG.debug("Deleting Namespace {}", name);
        client.namespaces().withName(name).delete();
        waitForNamespaceDeleted(client, name);
    }

    public static void deployClusterOperator(KubernetesClient client, String namespace, String... featureGates) throws Exception {
        LOG.debug("Deploying Cluster Operator in Namespace {}", namespace);
        for (HasMetadata resource : loadStrimziResources(client)) {
            if (resource instanceof ServiceAccount) {
                LOG.debug("Creating {} {} in Namespace {}", resource.getKind(), resource.getMetadata().getName(), namespace);
                resource.getMetadata().setNamespace(namespace);
                ServiceAccount sa = (ServiceAccount) resource;
                client.serviceAccounts().inNamespace(namespace).resource(sa).createOrReplace();
            } else if (resource instanceof ClusterRole) {
                LOG.debug("Creating {} {}", resource.getKind(), resource.getMetadata().getName());
                ClusterRole cr = (ClusterRole) resource;
                client.rbac().clusterRoles().resource(cr).createOrReplace();
            } else if (resource instanceof ClusterRoleBinding) {
                LOG.debug("Creating {} {}", resource.getKind(), resource.getMetadata().getName());
                ClusterRoleBinding crb = (ClusterRoleBinding) resource;
                crb.getSubjects().forEach(sbj -> sbj.setNamespace(namespace));
                client.rbac().clusterRoleBindings().resource(crb).createOrReplace();
            } else if (resource instanceof RoleBinding) {
                resource.getMetadata().setNamespace(namespace);
                RoleBinding rb = (RoleBinding) resource;
                rb.getSubjects().forEach(sbj -> sbj.setNamespace(namespace));
                // watch all namespaces
                ClusterRoleBinding crb = new ClusterRoleBindingBuilder()
                    .withNewMetadata()
                    .withName(format("%s-all-ns", rb.getMetadata().getName()))
                    .withAnnotations(rb.getMetadata().getAnnotations())
                    .withLabels(rb.getMetadata().getLabels())
                    .endMetadata()
                    .withRoleRef(rb.getRoleRef())
                    .withSubjects(rb.getSubjects())
                    .build();
                LOG.debug("Creating {} {}", crb.getKind(), crb.getMetadata().getName());
                client.rbac().clusterRoleBindings().resource(crb).createOrReplace();
            } else if (resource instanceof CustomResourceDefinition) {
                LOG.debug("Creating {} {}", resource.getKind(), resource.getMetadata().getName());
                CustomResourceDefinition crd = (CustomResourceDefinition) resource;
                client.apiextensions().v1().customResourceDefinitions().resource(crd).createOrReplace();
            } else if (resource instanceof ConfigMap) {
                LOG.debug("Creating {} {} in Namespace {}", resource.getKind(), resource.getMetadata().getName(), namespace);
                resource.getMetadata().setNamespace(namespace);
                ConfigMap cm = (ConfigMap) resource;
                client.configMaps().inNamespace(namespace).resource(cm).createOrReplace();
            } else if (resource instanceof Deployment) {
                LOG.debug("Creating {} {} in Namespace {}", resource.getKind(), resource.getMetadata().getName(), namespace);
                resource.getMetadata().setNamespace(namespace);
                Deployment deploy = (Deployment) resource;
                List<EnvVar> envVars = deploy.getSpec().getTemplate().getSpec().getContainers().stream()
                    .filter(con -> "strimzi-cluster-operator".equals(con.getName())).findFirst().orElseThrow().getEnv();
                // watch all namespaces
                EnvVar namespaceEnvVar = envVars.stream()
                    .filter(env -> "STRIMZI_NAMESPACE".equals(env.getName())).findFirst().orElseThrow();
                namespaceEnvVar.setValueFrom(null);
                namespaceEnvVar.setValue("*");
                // enable feature gates
                if (featureGates != null && featureGates.length > 0) {
                    EnvVar featureGatesEnvVar = envVars.stream()
                        .filter(env -> "STRIMZI_FEATURE_GATES".equals(env.getName())).findFirst().orElseThrow();
                    featureGatesEnvVar.setValueFrom(null);
                    featureGatesEnvVar.setValue(String.join(",", featureGates));
                }
                client.apps().deployments().inNamespace(namespace).resource(deploy).createOrReplace();
            } else {
                LOG.warn("Unknown resource {} {}", resource.getKind(), resource.getMetadata().getName());
            }
        }
    }
    
    public static void createCluster(KubernetesClient client, String namespace, String name) {
        int defaultPartitions = 3;
        int defaultReplicas = 3;
        int minISR = defaultReplicas - 1;

        Kafka kafka = new KafkaBuilder()
            .withMetadata(new ObjectMetaBuilder()
                .withName(name)
                .build())
            .withNewSpec()
            .withNewZookeeper()
            .withReplicas(3)
            .withNewEphemeralStorage()
            .endEphemeralStorage()
            .endZookeeper()
            .withNewKafka()
            .withReplicas(3)
            .withConfig(Map.of(
                "num.partitions", defaultPartitions,
                "default.replication.factor", defaultReplicas,
                "min.insync.replicas", minISR,
                "offsets.topic.replication.factor", defaultReplicas,
                "transaction.state.log.replication.factor", defaultReplicas,
                "transaction.state.log.min.isr", minISR,
                "auto.create.topics.enable", "false"
            ))
            .withListeners(new GenericKafkaListenerBuilder()
                .withName("plain")
                .withType(KafkaListenerType.INTERNAL)
                .withPort(9092)
                .withTls(false)
                .build())
            .withNewEphemeralStorage()
            .endEphemeralStorage()
            .endKafka()
            .withNewEntityOperator()
            .withNewTopicOperator()
            .withReconciliationIntervalSeconds(TO_RECONCILIATION_INTERVAL_SEC)
            .endTopicOperator()
            .withNewUserOperator()
            .endUserOperator()
            .withTemplate(new EntityOperatorTemplateBuilder()
                .withNewTopicOperatorContainer()
                .withEnv(
                    /*new ContainerEnvVarBuilder()
                        .withName("STRIMZI_USE_FINALIZERS")
                        .withValue("false")
                        .build(),*/
                    new ContainerEnvVarBuilder()
                        .withName("STRIMZI_MAX_QUEUE_SIZE")
                        .withValue(valueOf(TO_MAX_QUEUE_SIZE))
                        .build(),
                    new ContainerEnvVarBuilder()
                        .withName("STRIMZI_MAX_BATCH_SIZE")
                        .withValue(valueOf(TO_MAX_BATCH_SIZE))
                        .build(),
                    new ContainerEnvVarBuilder()
                        .withName("STRIMZI_MAX_BATCH_LINGER_MS")
                        .withValue(valueOf(TO_MAX_BATCH_LINGER_MS))
                        .build())
                .endTopicOperatorContainer()
                .build())
            .endEntityOperator()
            .endSpec()
            .build();

        LOG.debug("Creating Kafka {} in Namespace {}", name, namespace);
        Crds.kafkaOperation(client).inNamespace(namespace).resource(kafka).createOrReplace();
        waitForKafkaReady(client, namespace, name);
    }

    public static void restartEntityOperator(KubernetesClient client, String namespace, String clusterName) {
        LOG.debug("Restarting Entity Operator in Namespace {}", namespace);
        client.pods().inNamespace(namespace)
            .withLabel("strimzi.io/name", format("%s-entity-operator", clusterName))
            .delete();
        waitForEntityOperatorReady(client, namespace, clusterName);
    }

    public static void createKafkaTopic(KubernetesClient client, String namespace, String clusterName, String name) {
        KafkaTopic kt = new KafkaTopicBuilder()
            .withNewMetadata()
            .withName(name)
            .withLabels(Map.of("strimzi.io/cluster", clusterName))
            .endMetadata()
            .withNewSpec()
            .withConfig(Map.of("min.insync.replicas", "2"))
            .withPartitions(3)
            .withReplicas(3)
            .endSpec()
            .build();
        LOG.debug("Creating KafkaTopic {} in Namespace {}", name, namespace);
        Crds.topicOperation(client).inNamespace(namespace).resource(kt).createOrReplace();
        waitForKafkaTopicReady(client, namespace, name);
    }

    public static void updateKafkaTopic(KubernetesClient client, String namespace, String name) {
        LOG.debug("Updating KafkaTopic {} in Namespace {}", name, namespace);
        Crds.topicOperation(client).inNamespace(namespace).withName(name)
            .edit(k -> new KafkaTopicBuilder(k)
                .editSpec()
                .withPartitions(5)
                .withConfig(Map.of("retention.bytes", "1073741824"))
                .endSpec()
                .build());
        waitForKafkaTopicReady(client, namespace, name);
    }

    public static void deleteKafkaTopic(KubernetesClient client, String namespace, String name) {
        LOG.debug("Deleting KafkaTopic {} in Namespace {}", name, namespace);
        Crds.topicOperation(client).inNamespace(namespace).withName(name).delete();
        waitForKafkaTopicDeleted(client, namespace, name);
    }

    public static void cleanKubernetes(KubernetesClient client, String... namespaces) throws Exception {
        LOG.debug("Cleaning up Kubernetes");
        for (String ns : namespaces) {
            deleteNamespace(client, ns);
        }
        for (HasMetadata resource : loadStrimziResources(client)) {
            if (resource instanceof ClusterRole) {
                LOG.debug("Deleting {} {}", resource.getKind(), resource.getMetadata().getName());
                ClusterRole cr = (ClusterRole) resource;
                client.rbac().clusterRoles().resource(cr).delete();
            } else if (resource instanceof ClusterRoleBinding) {
                LOG.debug("Deleting {} {}", resource.getKind(), resource.getMetadata().getName());
                ClusterRoleBinding crb = (ClusterRoleBinding) resource;
                client.rbac().clusterRoleBindings().resource(crb).delete();
            } else if (resource instanceof CustomResourceDefinition) {
                LOG.debug("Deleting {} {}", resource.getKind(), resource.getMetadata().getName());
                CustomResourceDefinition crd = (CustomResourceDefinition) resource;
                client.apiextensions().v1().customResourceDefinitions().resource(crd).delete();
            }
        }
    }

    private static void waitForNamespaceReady(KubernetesClient client, String name) {
        LOG.debug("Waiting for Namespace {} to be ready", name);
        long timeoutSec = ofMinutes(5).toSeconds();
        while (client.namespaces().withName(name).get() == null && timeoutSec-- > 0) {
            try {
                SECONDS.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void waitForNamespaceDeleted(KubernetesClient client, String name) {
        LOG.debug("Waiting for Namespace {} to be deleted", name);
        long timeoutSec = ofMinutes(5).toSeconds();
        while (client.namespaces().withName(name).get() != null && timeoutSec-- > 0) {
            try {
                SECONDS.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void waitForKafkaReady(KubernetesClient client, String namespace, String name) {
        LOG.debug("Waiting for Kafka {} to be ready", name);
        Crds.kafkaOperation(client).inNamespace(namespace).withName(name).waitUntilCondition(k -> {
            if (k.getStatus() != null && k.getStatus().getConditions() != null) {
                return k.getMetadata().getGeneration() == k.getStatus().getObservedGeneration()
                    && k.getStatus().getConditions().stream()
                    .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
            } else {
                return false;
            }
        }, 5, MINUTES);
    }

    private static void waitForEntityOperatorReady(KubernetesClient client, String namespace, String clusterName) {
        client.pods().inNamespace(namespace)
            .withLabel("strimzi.io/name", format("%s-entity-operator", clusterName))
            .waitUntilReady(5, MINUTES);
    }

    private static void waitForKafkaTopicReady(KubernetesClient client, String namespace, String name) {
        LOG.debug("Waiting for KafkaTopic {} to be ready", name);
        Crds.topicOperation(client).inNamespace(namespace).withName(name).waitUntilCondition(k -> {
            if (k.getStatus() != null && k.getStatus().getConditions() != null) {
                return k.getMetadata().getGeneration() == k.getStatus().getObservedGeneration()
                    && k.getStatus().getConditions().stream()
                    .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
            } else {
                return false;
            }
        }, 5, MINUTES);
    }

    private static void waitForKafkaTopicDeleted(KubernetesClient client, String namespace, String name) {
        LOG.debug("Waiting for KafkaTopic {} to be deleted", name);
        long timeoutSec = ofMinutes(5).toSeconds();
        while (Crds.topicOperation(client).inNamespace(namespace).withName(name).get() != null && timeoutSec-- > 0) {
            try {
                SECONDS.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}