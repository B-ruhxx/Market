package com.tienda.ecommerce.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StorageSecurityPolicyTest {

    @Test
    @DisplayName("Manifiestos K8s mantienen valores fail-secure (no root, no demo-seed, validate DDL, no localhost CORS, body-size 6m)")
    @SuppressWarnings("unchecked")
    void k8s_politicasDeSeguridad() throws Exception {
        Yaml yaml = new Yaml();

        Map<String, Object> configMap;
        try (var fis = new FileInputStream("k8s/01-configmap.yaml")) {
            configMap = yaml.load(fis);
        }
        Map<String, String> data = (Map<String, String>) configMap.get("data");
        assertEquals("false", data.get("DEMO_SEED_ENABLED"));
        assertEquals("validate", data.get("SPRING_JPA_HIBERNATE_DDL_AUTO"));
        assertEquals("gcs", data.get("APP_STORAGE_TYPE"));
        assertFalse(data.getOrDefault("CORS_ORIGINS", "").contains("localhost"));

        List<Map<String, Object>> appDocs = new java.util.ArrayList<>();
        try (var fis = new FileInputStream("k8s/04-app.yaml")) {
            for (Object doc : yaml.loadAll(fis)) {
                if (doc instanceof Map<?, ?> map) {
                    appDocs.add((Map<String, Object>) map);
                }
            }
        }
        Map<String, Object> deployment = appDocs.stream()
                .filter(d -> "Deployment".equals(d.get("kind")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> spec = (Map<String, Object>) deployment.get("spec");
        Map<String, Object> template = (Map<String, Object>) spec.get("template");
        Map<String, Object> podSpec = (Map<String, Object>) template.get("spec");
        Map<String, Object> podSec = (Map<String, Object>) podSpec.get("securityContext");
        assertEquals(Boolean.TRUE, podSec.get("runAsNonRoot"));

        List<Map<String, Object>> containers = (List<Map<String, Object>>) podSpec.get("containers");
        Map<String, Object> containerSec = (Map<String, Object>) containers.get(0).get("securityContext");
        assertEquals(Boolean.FALSE, containerSec.get("allowPrivilegeEscalation"));

        List<Map<String, Object>> hpaIngress = new java.util.ArrayList<>();
        try (var fis = new FileInputStream("k8s/05-hpa-and-ingress.yaml")) {
            for (Object doc : yaml.loadAll(fis)) {
                if (doc instanceof Map<?, ?> map) {
                    hpaIngress.add((Map<String, Object>) map);
                }
            }
        }
        Map<String, Object> ingress = hpaIngress.stream()
                .filter(d -> "Ingress".equals(d.get("kind")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> ingressSpec = (Map<String, Object>) ingress.get("spec");
        assertEquals("nginx", ingressSpec.get("ingressClassName"));
        Map<String, Object> meta = (Map<String, Object>) ingress.get("metadata");
        Map<String, String> annotations = (Map<String, String>) meta.get("annotations");
        assertEquals("6m", annotations.get("nginx.ingress.kubernetes.io/proxy-body-size"));
        assertFalse(annotations.containsKey("kubernetes.io/ingress.class"));

        String redisYaml = Files.readString(Path.of("k8s/03b-redis.yaml"));
        assertTrue(redisYaml.contains("automountServiceAccountToken: false"));
        assertTrue(redisYaml.contains("PersistentVolumeClaim"));
        assertTrue(redisYaml.contains("/etc/redis-secret/REDIS_PASSWORD"));
        assertTrue(redisYaml.contains("type: Recreate"));
        assertTrue(redisYaml.contains("electronix-redis-secret"));

        String postgresYaml = Files.readString(Path.of("k8s/03-postgres.yaml"));
        assertTrue(postgresYaml.contains("runAsNonRoot: true"));
        assertTrue(postgresYaml.contains("type: Recreate"));
        assertTrue(postgresYaml.contains("electronix-db-secret"));

        String secretsExample = Files.readString(Path.of("k8s/02-secret.yaml.example"));
        assertTrue(secretsExample.contains("electronix-db-secret"));
        assertTrue(secretsExample.contains("electronix-redis-secret"));
        assertTrue(secretsExample.contains("electronix-app-secret"));
        assertTrue(secretsExample.contains("electronix-monitoring-secret"));

        String netPolYaml = Files.readString(Path.of("k8s/06-pdb-and-networkpolicy.yaml"));
        assertTrue(netPolYaml.contains("isolate-prometheus"));
        assertTrue(netPolYaml.contains("isolate-grafana"));
        assertTrue(netPolYaml.contains("port: 3000"));
        assertTrue(netPolYaml.contains("isolate-electronix-app"));
        assertTrue(netPolYaml.contains("169.254.169.254/32"));

        String ingressYaml = Files.readString(Path.of("k8s/05-hpa-and-ingress.yaml"));
        assertTrue(ingressYaml.contains("/actuator/(prometheus|metrics)"));

        String monitoringYaml = Files.readString(Path.of("k8s/07-monitoring.yaml"));
        assertTrue(monitoringYaml.contains("kubernetes_sd_configs"));
        assertTrue(monitoringYaml.contains("electronix-monitoring-secret"));
        assertTrue(monitoringYaml.contains("livenessProbe"));
        assertFalse(monitoringYaml.contains("electronix-app-service:80"));
    }

    @Test
    @DisplayName("Terraform aplica mínimo privilegio en GCS IAM, scope de VM, SSL en Cloud SQL y CORS sin comodín '*'")
    void terraform_politicasMinimoPrivilegio() throws Exception {
        String mainTf = Files.readString(Path.of("terraform/main.tf"));
        String varsTf = Files.readString(Path.of("terraform/variables.tf"));

        assertTrue(mainTf.contains("roles/storage.objectCreator"));
        assertFalse(mainTf.contains("roles/storage.objectAdmin"));
        assertTrue(mainTf.contains("roles/storage.legacyObjectReader"));
        assertFalse(mainTf.contains("roles/storage.objectViewer"));
        assertFalse(mainTf.contains("\"cloud-platform\""));
        assertTrue(mainTf.contains("https://www.googleapis.com/auth/devstorage.read_write"));
        assertTrue(mainTf.contains("ENCRYPTED_ONLY"));
        assertTrue(mainTf.contains("shielded_instance_config"));
        assertTrue(mainTf.contains("enable-oslogin"));
        assertFalse(varsTf.contains("default     = [\"*\"]"));
        assertFalse(varsTf.contains("default     = \"\""));
    }

    @Test
    @DisplayName("Docker Compose, .gitignore y pom.xml mantienen higiene de secretos y exposición local")
    void repo_politicasHigieneYExposicion() throws Exception {
        String composeDev = Files.readString(Path.of("docker-compose.yml"));
        assertTrue(composeDev.contains("127.0.0.1:5435:5432"));
        assertTrue(composeDev.contains("127.0.0.1:8090:8080"));
        assertTrue(composeDev.contains("127.0.0.1:8091:8080"));
        assertFalse(composeDev.contains("electronix-dev-scrape-token"));
        assertFalse(composeDev.contains("trycloudflare.com"));
        assertFalse(composeDev.contains("redis-cli -a"));

        String composeProd = Files.readString(Path.of("docker-compose.prod.yml"));
        assertFalse(composeProd.contains("redis-cli -a"));
        assertTrue(composeProd.contains("no-new-privileges:true"));

        String appYml = Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(appYml.contains("forward-headers-strategy: native"));
        assertFalse(appYml.contains("forward-headers-strategy: framework"));

        String gitignore = Files.readString(Path.of(".gitignore"));
        assertTrue(gitignore.contains("*.tfvars.json"));
        assertTrue(gitignore.contains("k8s/*secret*.yml"));
        assertTrue(gitignore.contains("__pycache__/"));

        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<artifactId>h2</artifactId>\n\t\t\t<scope>test</scope>"));
    }
}
