package com.nexlyn.bgv.documents.internal.storage;

import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The S3 adapter against real S3-compatible servers: S3Mock (a strict S3 stand-in, used for most
 * checks) and SeaweedFS (the store the local Docker Compose stack uses), so a problem with the
 * local store shows up here and not on a developer's screen.
 */
class S3StorageServiceTest {

    static GenericContainer<?> s3mock;
    static GenericContainer<?> seaweed;

    @BeforeAll
    static void start() {
        s3mock = new GenericContainer<>("adobe/s3mock:latest")
                .withExposedPorts(9090)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(90)));
        s3mock.start();
        seaweed = new GenericContainer<>("chrislusf/seaweedfs:latest")
                .withExposedPorts(8333)
                // The very identity file the local Compose stack uses (working directory of a test run = the module).
                .withCopyFileToContainer(MountableFile.forHostPath(Path.of("../../../infra/local/seaweedfs/s3.json").toAbsolutePath().normalize()), "/etc/seaweedfs/s3.json")
                .withCommand("server", "-dir=/data", "-s3", "-s3.port=8333", "-s3.config=/etc/seaweedfs/s3.json", "-master.volumeSizeLimitMB=64", "-volume.max=5")
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(90)));
        seaweed.start();
    }

    @AfterAll
    static void stop() {
        if (s3mock != null) {
            s3mock.stop();
        }
        if (seaweed != null) {
            seaweed.stop();
        }
    }

    private static S3StorageService service(GenericContainer<?> container, int port, String bucket, boolean createBucket, boolean sse) {
        String endpoint = "http://" + container.getHost() + ":" + container.getMappedPort(port);
        return new S3StorageService(new StorageProperties(endpoint, "ap-south-1", bucket, "local", "local-dev-secret", true, sse, createBucket));
    }

    /** SeaweedFS answers a moment after the port opens; retry the first call for a while. */
    private static void putEventually(S3StorageService service, String key, byte[] bytes) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;
        while (true) {
            try {
                service.put(key, bytes, "application/octet-stream");
                return;
            } catch (ApiException e) {
                if (System.currentTimeMillis() > deadline) {
                    throw e;
                }
                Thread.sleep(1000);
            }
        }
    }

    @Test
    void storesReadsAndDeletesOnS3Mock() {
        S3StorageService store = service(s3mock, 9090, "test-bucket", true, true);
        byte[] bytes = "hello, documents".getBytes(StandardCharsets.UTF_8);

        store.put("cases/abc/one", bytes, "text/plain");
        assertThat(store.get("cases/abc/one")).isEqualTo(bytes);

        store.delete("cases/abc/one");
        assertThatThrownBy(() -> store.get("cases/abc/one"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void storesBinaryContentWithoutChangingIt() {
        S3StorageService store = service(s3mock, 9090, "test-bucket", true, false);
        byte[] bytes = new byte[100_000];
        new java.util.Random(7).nextBytes(bytes);
        store.put("cases/abc/binary", bytes, "application/octet-stream");
        assertThat(store.get("cases/abc/binary")).isEqualTo(bytes);
    }

    @Test
    void aMissingBucketIsAPlainServiceUnavailable() {
        S3StorageService store = service(s3mock, 9090, "no-such-bucket", false, false);
        assertThatThrownBy(() -> store.put("k", new byte[]{1}, "text/plain"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).doesNotContain("no-such-bucket").doesNotContain("Exception");
                });
    }

    @Test
    void anUnreachableStoreIsAPlainServiceUnavailable() {
        S3StorageService store = new S3StorageService(new StorageProperties("http://localhost:1", "ap-south-1", "b", "a", "s", true, false, false));
        assertThatThrownBy(() -> store.get("k")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    void notConfiguredSaysSoWithoutTryingToConnect() {
        S3StorageService store = new S3StorageService(new StorageProperties("", "ap-south-1", "", "", "", false, false, false));
        assertThatThrownBy(() -> store.put("k", new byte[]{1}, "text/plain")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
            assertThat(e.getMessage()).contains("not set up");
        });
    }

    @Test
    void theLocalSeaweedFsStoreWorksWithTheSameSettingsAsCompose() throws InterruptedException {
        // Same settings as infra/local/docker-compose.yml: path style, no server-side encryption, bucket created on first use.
        S3StorageService store = service(seaweed, 8333, "nexlyn-bgv", true, false);
        byte[] bytes = "local store".getBytes(StandardCharsets.UTF_8);

        putEventually(store, "cases/abc/one", bytes);
        assertThat(store.get("cases/abc/one")).isEqualTo(bytes);
        store.delete("cases/abc/one");
        assertThatThrownBy(() -> store.get("cases/abc/one"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.NOT_FOUND));
    }
}
