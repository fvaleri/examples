package it.fvaleri.example;

import org.apache.kafka.common.Uuid;
import org.apache.kafka.server.log.remote.storage.LogSegmentData;
import org.apache.kafka.server.log.remote.storage.RemoteLogSegmentMetadata;
import org.apache.kafka.server.log.remote.storage.RemoteResourceNotFoundException;
import org.apache.kafka.server.log.remote.storage.RemoteStorageException;
import org.apache.kafka.server.log.remote.storage.RemoteStorageManager;

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

/**
 * Simple RemoteStorageManager implementation that uses local disk as remote storage.
 */
public class SimpleRemoteStorageManager implements RemoteStorageManager {
    public Path tmpdir;
    
    private Optional<Path> getIndexFilePath(LogSegmentData logSegmentData, RemoteStorageManager.IndexType indexType) {
        switch (indexType) {
            case OFFSET:
                return Optional.of(logSegmentData.offsetIndex());
            case PRODUCER_SNAPSHOT:
                return Optional.of(logSegmentData.producerSnapshotIndex());
            case TIMESTAMP:
                return Optional.of(logSegmentData.timeIndex());
            case TRANSACTION:
                return logSegmentData.transactionIndex();
            default:
                throw new IllegalArgumentException(String.format("index type %s does not have a file path", indexType));
        }
    }
    
    @Override
    public Optional<RemoteLogSegmentMetadata.CustomMetadata>  copyLogSegmentData(RemoteLogSegmentMetadata remoteLogSegmentMetadata, LogSegmentData logSegmentData) throws RemoteStorageException {
        System.out.println("copyLogSegmentData:" + remoteLogSegmentMetadata + ";;" + tmpdir.toString());
        try {
            Files.copy(logSegmentData.logSegment(), tmpdir.resolve(getBlobName(remoteLogSegmentMetadata, "segment")));
            System.out.println("copy to " + tmpdir.resolve(getBlobName(remoteLogSegmentMetadata, "segment")));
            for (IndexType indexType : IndexType.values()) {
                if (indexType == IndexType.LEADER_EPOCH) {
                    Files.write(tmpdir.resolve(getBlobName(remoteLogSegmentMetadata, indexType.toString())), logSegmentData.leaderEpochIndex().array());
                } else {
                    Optional<Path> path = getIndexFilePath(logSegmentData, indexType);
                    if (path.isPresent()) {
                        Files.copy(path.get(), tmpdir.resolve(getBlobName(remoteLogSegmentMetadata, indexType.toString())));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    private String getBlobName(RemoteLogSegmentMetadata remoteLogSegmentMetadata, String suffix) {
        int partition = remoteLogSegmentMetadata.remoteLogSegmentId().topicIdPartition().topicPartition().partition();
        Uuid id = remoteLogSegmentMetadata.remoteLogSegmentId().id();
        String logSegmentId = new UUID(id.getMostSignificantBits(), id.getLeastSignificantBits()).toString();
        return String.format("%d.%s.%s", partition, logSegmentId, suffix);
    }

    @Override
    public InputStream fetchLogSegment(RemoteLogSegmentMetadata remoteLogSegmentMetadata, int startPosition) throws RemoteStorageException {
        return fetchLogSegment(remoteLogSegmentMetadata, startPosition, Integer.MAX_VALUE);

    }

    @Override
    public InputStream fetchLogSegment(RemoteLogSegmentMetadata remoteLogSegmentMetadata, int startPosition, int endPosition) throws RemoteStorageException {
        System.out.println("fetchLogSegment:" + remoteLogSegmentMetadata);
        String name = getBlobName(remoteLogSegmentMetadata, "segment");
        Path path = tmpdir.resolve(name);
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
        System.out.println("fetchIndex:" + indexType + "; metadata:" + remoteLogSegmentMetadata);
        String name = getBlobName(remoteLogSegmentMetadata, indexType.toString());
        Path path = tmpdir.resolve(name);
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
        System.out.println("deleteLogSegmentData:" + remoteLogSegmentMetadata);
        String name = getBlobName(remoteLogSegmentMetadata, "segment");
        Path path = tmpdir.resolve(name);
        try {
            Files.deleteIfExists(path);
            for (IndexType indexType : IndexType.values()) {
                name = getBlobName(remoteLogSegmentMetadata, indexType.toString());
                path = tmpdir.resolve(name);
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public void configure(Map<String, ?> configs) {
        try {
            String fileName = "/tmp/remote";
            tmpdir = Paths.get(fileName);
            if (!Files.exists(tmpdir)) {
                Files.createDirectory(tmpdir);
                System.out.println("Directory created:" + tmpdir);
            } else {
                System.out.println("Directory already exists");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
