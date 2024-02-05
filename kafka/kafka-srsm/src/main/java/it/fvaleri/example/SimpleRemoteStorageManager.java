package it.fvaleri.example;

import org.apache.kafka.common.Uuid;
import org.apache.kafka.server.log.remote.storage.LogSegmentData;
import org.apache.kafka.server.log.remote.storage.RemoteLogSegmentMetadata;
import org.apache.kafka.server.log.remote.storage.RemoteResourceNotFoundException;
import org.apache.kafka.server.log.remote.storage.RemoteStorageException;
import org.apache.kafka.server.log.remote.storage.RemoteStorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.lang.String.format;

/**
 * Simple RemoteStorageManager implementation that uses local disk as remote storage.
 */
public class SimpleRemoteStorageManager implements RemoteStorageManager {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleRemoteStorageManager.class);
    private static final Path TMP_DIR = Paths.get("/tmp/remote");

    @Override
    public void configure(Map<String, ?> configs) {
        try {
            if (!Files.exists(TMP_DIR)) {
                Files.createDirectory(TMP_DIR);
                LOG.info("Directory created: {}", TMP_DIR);
            } else {
                LOG.info("Directory {} already exists", TMP_DIR);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
    }
    
    @Override
    public Optional<RemoteLogSegmentMetadata.CustomMetadata> copyLogSegmentData(RemoteLogSegmentMetadata remoteLogSegmentMetadata, LogSegmentData logSegmentData) throws RemoteStorageException {
        LOG.info("Invoked copyLogSegmentData: {}", remoteLogSegmentMetadata);
        try {
            Files.copy(logSegmentData.logSegment(), TMP_DIR.resolve(getBlobName(remoteLogSegmentMetadata, "segment")));
            LOG.info("Copy to {}", TMP_DIR.resolve(getBlobName(remoteLogSegmentMetadata, "segment")));
            for (IndexType indexType : IndexType.values()) {
                if (indexType == IndexType.LEADER_EPOCH) {
                    Files.write(TMP_DIR.resolve(getBlobName(remoteLogSegmentMetadata, indexType.toString())), logSegmentData.leaderEpochIndex().array());
                } else {
                    Optional<Path> path = getIndexFilePath(logSegmentData, indexType);
                    if (path.isPresent()) {
                        Files.copy(path.get(), TMP_DIR.resolve(getBlobName(remoteLogSegmentMetadata, indexType.toString())));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    @Override
    public InputStream fetchLogSegment(RemoteLogSegmentMetadata remoteLogSegmentMetadata, int startPosition) throws RemoteStorageException {
        return fetchLogSegment(remoteLogSegmentMetadata, startPosition, Integer.MAX_VALUE);
    }

    @Override
    public InputStream fetchLogSegment(RemoteLogSegmentMetadata remoteLogSegmentMetadata, int startPosition, int endPosition) throws RemoteStorageException {
        LOG.info("Invoked fetchLogSegment: {}", remoteLogSegmentMetadata);
        String name = getBlobName(remoteLogSegmentMetadata, "segment");
        Path path = TMP_DIR.resolve(name);
        try {
            byte[] content = Files.readAllBytes(path);
            int length = Math.min(content.length - 1, endPosition) - startPosition + 1;
            return new ByteArrayInputStream(content, startPosition, length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InputStream fetchIndex(RemoteLogSegmentMetadata remoteLogSegmentMetadata, IndexType indexType) throws RemoteStorageException {
        LOG.info("Invoked fetchIndex: {}; metadata: {}", indexType, remoteLogSegmentMetadata);
        String name = getBlobName(remoteLogSegmentMetadata, indexType.toString());
        Path path = TMP_DIR.resolve(name);
        try {
            byte[] content = Files.readAllBytes(path);
            return new ByteArrayInputStream(content);
        } catch (NoSuchFileException e) {
            if (indexType != IndexType.TRANSACTION) {
                throw new RemoteResourceNotFoundException(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public void deleteLogSegmentData(RemoteLogSegmentMetadata remoteLogSegmentMetadata) throws RemoteStorageException {
        LOG.info("Invoked deleteLogSegmentData: {}", remoteLogSegmentMetadata);
        String name = getBlobName(remoteLogSegmentMetadata, "segment");
        Path path = TMP_DIR.resolve(name);
        try {
            Files.deleteIfExists(path);
            for (IndexType indexType : IndexType.values()) {
                name = getBlobName(remoteLogSegmentMetadata, indexType.toString());
                path = TMP_DIR.resolve(name);
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getBlobName(RemoteLogSegmentMetadata remoteLogSegmentMetadata, String suffix) {
        int partition = remoteLogSegmentMetadata.remoteLogSegmentId().topicIdPartition().topicPartition().partition();
        Uuid id = remoteLogSegmentMetadata.remoteLogSegmentId().id();
        String logSegmentId = new UUID(id.getMostSignificantBits(), id.getLeastSignificantBits()).toString();
        return format("%d.%s.%s", partition, logSegmentId, suffix);
    }

    private Optional<Path> getIndexFilePath(LogSegmentData logSegmentData, RemoteStorageManager.IndexType indexType) {
        switch (indexType) {
            case OFFSET:
                return Optional.of(logSegmentData.offsetIndex());
            case TIMESTAMP:
                return Optional.of(logSegmentData.timeIndex());
            case TRANSACTION:
                return logSegmentData.transactionIndex();
            case PRODUCER_SNAPSHOT:
                return Optional.of(logSegmentData.producerSnapshotIndex());
            default:
                throw new IllegalArgumentException(format("Index type %s does not have a file path", indexType));
        }
    }
}
