package it.fvaleri.example;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static java.lang.String.format;
import static java.lang.String.valueOf;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Utils {
    private static final String STRIMZI_VERSION = "0.42.0";
    private static final String OPERATOR_IMAGE = "quay.io/streams/operator:latest";
    private static final String KAFKA_IMAGE = "quay.io/streams/kafka:latest-kafka-3.7.1";
    
    private static final long KUBERNETES_TIMEOUT_MS = 300_000;
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
            // ignore
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

    private static List<HasMetadata> loadStrimziResources(KubernetesClient client) {
        String strimziUrl = format("https://github.com/strimzi/strimzi-kafka-operator/releases/download/%s/strimzi-cluster-operator-%s.yaml", STRIMZI_VERSION, STRIMZI_VERSION);
        try {
            return client.load(new BufferedInputStream(new URL(strimziUrl).openStream())).items();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void createNamespace(KubernetesClient client, String name) {
        if (client.namespaces().withName(name).get() == null) {
            System.out.printf("Creating Namespace %s%n", name);
            client.namespaces().resource(new NamespaceBuilder()
                .withNewMetadata()
                .withName(name)
                .endMetadata()
                .build()).create();
            waitForNamespaceReady(client, name);
        }
    }

    public static void deleteNamespace(KubernetesClient client, String name) {
        System.out.printf("Deleting Namespace %s%n", name);
        client.namespaces().withName(name).delete();
        waitForNamespaceDeleted(client, name);
    }

    public static void deployClusterOperator(KubernetesClient client, String namespace, String... featureGates) {
        System.out.printf("Deploying Cluster Operator in Namespace %s%n", namespace);
        for (HasMetadata resource : loadStrimziResources(client)) {
            if (resource instanceof ServiceAccount sa) {
                System.out.printf("Creating %s %s in Namespace %s%n", resource.getKind(), resource.getMetadata().getName(), namespace);
                sa.getMetadata().setNamespace(namespace);
                client.serviceAccounts().inNamespace(namespace).resource(sa).withTimeout(KUBERNETES_TIMEOUT_MS, MILLISECONDS).delete();
                client.serviceAccounts().inNamespace(namespace).resource(sa).create();
            } else if (resource instanceof ClusterRole cr) {
                System.out.printf("Creating %s %s%n", resource.getKind(), resource.getMetadata().getName());
                client.rbac().clusterRoles().resource(cr).withTimeout(KUBERNETES_TIMEOUT_MS, MILLISECONDS).delete();
                client.rbac().clusterRoles().resource(cr).create();
            } else if (resource instanceof ClusterRoleBinding crb) {
                System.out.printf("Creating %s %s%n", resource.getKind(), resource.getMetadata().getName());
                crb.getSubjects().forEach(sbj -> sbj.setNamespace(namespace));
                client.rbac().clusterRoleBindings().resource(crb).withTimeout(KUBERNETES_TIMEOUT_MS, MILLISECONDS).delete();
                client.rbac().clusterRoleBindings().resource(crb).create();
            } else if (resource instanceof RoleBinding rb) {
                resource.getMetadata().setNamespace(namespace);
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
                System.out.printf("Creating %s %s%n", crb.getKind(), crb.getMetadata().getName());
                client.rbac().clusterRoleBindings().resource(crb).withTimeout(KUBERNETES_TIMEOUT_MS, MILLISECONDS).delete();
                client.rbac().clusterRoleBindings().resource(crb).create();
            } else if (resource instanceof CustomResourceDefinition crd) {
                System.out.printf("Creating %s %s%n", resource.getKind(), resource.getMetadata().getName());
                client.apiextensions().v1().customResourceDefinitions().resource(crd).withTimeout(KUBERNETES_TIMEOUT_MS, MILLISECONDS).delete();
                client.apiextensions().v1().customResourceDefinitions().resource(crd).create();
            } else if (resource instanceof ConfigMap cm) {
                System.out.printf("Creating %s %s in Namespace %s%n", resource.getKind(), resource.getMetadata().getName(), namespace);
                cm.getMetadata().setNamespace(namespace);
                client.configMaps().inNamespace(namespace).resource(cm).withTimeout(KUBERNETES_TIMEOUT_MS, MILLISECONDS).delete();
                client.configMaps().inNamespace(namespace).resource(cm).create();
            } else if (resource instanceof Deployment deploy) {
                System.out.printf("Creating %s %s in Namespace %s%n", resource.getKind(), resource.getMetadata().getName(), namespace);
                deploy.getMetadata().setNamespace(namespace);
                deploy.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(OPERATOR_IMAGE);
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
                client.apps().deployments().inNamespace(namespace).resource(deploy).withTimeout(KUBERNETES_TIMEOUT_MS, MILLISECONDS).delete();
                client.apps().deployments().inNamespace(namespace).resource(deploy).create();
            } else {
                System.out.printf("Unknown resource %s %s%n", resource.getKind(), resource.getMetadata().getName());
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
            .withImage(KAFKA_IMAGE)
            .withReplicas(3)
            .withNewEphemeralStorage()
            .endEphemeralStorage()
            .endZookeeper()
            .withNewKafka()
            .withReplicas(3)
            .withImage(KAFKA_IMAGE)
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

        System.out.printf("Creating Kafka %s in Namespace %s%n", name, namespace);
        Crds.kafkaOperation(client).inNamespace(namespace).resource(kafka).create();
        waitForKafkaReady(client, namespace, name);
    }
    
    public static void createKafkaTopic(KubernetesClient client, String namespace, String clusterName, String name, boolean log) {
        if (log) System.out.printf("Creating KafkaTopic %s in Namespace %s%n", name, namespace);
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
        Crds.topicOperation(client).inNamespace(namespace).resource(kt).create();
        waitForKafkaTopicReady(client, namespace, name, log);
    }
    
    public static void updateKafkaTopic(KubernetesClient client, String namespace, String name, boolean log) {
        if (log) System.out.printf("Updating KafkaTopic %s in Namespace %s%n", name, namespace);
        Crds.topicOperation(client).inNamespace(namespace).withName(name)
            .edit(k -> new KafkaTopicBuilder(k)
                .editSpec()
                .withPartitions(5)
                .withConfig(Map.of("retention.bytes", "1073741824"))
                .endSpec()
                .build());
        waitForKafkaTopicReady(client, namespace, name, log);
    }

    public static void deleteKafkaTopic(KubernetesClient client, String namespace, String name, boolean log) {
        if (log) System.out.printf("Deleting KafkaTopic %s in Namespace %s%n", name, namespace);
        Crds.topicOperation(client).inNamespace(namespace).withName(name).delete();
        waitForKafkaTopicDeleted(client, namespace, name, log);
    }

    public static void deleteAllResources(KubernetesClient client, String... namespaces) {
        for (String namespace : namespaces) {
            System.out.printf("Deleting all KafkaTopic resources in Namespace %s%n", namespace);
            Crds.topicOperation(client).inNamespace(namespace).delete();
            deleteNamespace(client, namespace);
        }
        for (HasMetadata resource : loadStrimziResources(client)) {
            if (resource instanceof ClusterRole cr) {
                System.out.printf("Deleting %s %s%n", resource.getKind(), resource.getMetadata().getName());
                client.rbac().clusterRoles().resource(cr).delete();
            } else if (resource instanceof ClusterRoleBinding crb) {
                System.out.printf("Deleting %s %s%n", resource.getKind(), resource.getMetadata().getName());
                client.rbac().clusterRoleBindings().resource(crb).delete();
            } else if (resource instanceof CustomResourceDefinition crd) {
                System.out.printf("Deleting %s %s%n", resource.getKind(), resource.getMetadata().getName());
                client.apiextensions().v1().customResourceDefinitions().resource(crd).delete();
            }
        }
    }

    private static void waitForNamespaceReady(KubernetesClient client, String name) {
        System.out.printf("Waiting for Namespace %s to be ready%n", name);
        long timeoutSec = ofMillis(KUBERNETES_TIMEOUT_MS).toSeconds();
        while (client.namespaces().withName(name).get() == null && timeoutSec-- > 0) {
            try {
                SECONDS.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void waitForNamespaceDeleted(KubernetesClient client, String name) {
        System.out.printf("Waiting for Namespace %s to be deleted%n", name);
        long timeoutSec = ofMillis(KUBERNETES_TIMEOUT_MS).toSeconds();
        while (client.namespaces().withName(name).get() != null && timeoutSec-- > 0) {
            try {
                SECONDS.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void waitForKafkaReady(KubernetesClient client, String namespace, String name) {
        System.out.printf("Waiting for Kafka %s to be ready%n", name);
        Crds.kafkaOperation(client).inNamespace(namespace).withName(name).waitUntilCondition(k -> {
            if (k.getStatus() != null && k.getStatus().getConditions() != null) {
                return k.getMetadata().getGeneration() == k.getStatus().getObservedGeneration()
                    && k.getStatus().getConditions().stream()
                    .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
            } else {
                return false;
            }
        }, KUBERNETES_TIMEOUT_MS, MILLISECONDS);
    }

    private static void waitForKafkaTopicReady(KubernetesClient client, String namespace, String name, boolean log) {
        if (log) System.out.printf("Waiting for KafkaTopic %s to be ready%n", name);
        Crds.topicOperation(client).inNamespace(namespace).withName(name).waitUntilCondition(k -> {
            if (k.getStatus() != null && k.getStatus().getConditions() != null) {
                return k.getMetadata().getGeneration() == k.getStatus().getObservedGeneration()
                    && k.getStatus().getConditions().stream()
                    .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
            } else {
                return false;
            }
        }, KUBERNETES_TIMEOUT_MS, MILLISECONDS);
    }

    private static void waitForKafkaTopicDeleted(KubernetesClient client, String namespace, String name, boolean log) {
        if (log) System.out.printf("Waiting for KafkaTopic %s to be deleted%n", name);
        long timeoutSec = ofMillis(KUBERNETES_TIMEOUT_MS).toSeconds();
        while (Crds.topicOperation(client).inNamespace(namespace).withName(name).get() != null && timeoutSec-- > 0) {
            try {
                SECONDS.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
