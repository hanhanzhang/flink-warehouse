package com.sdu.streaming.warehouse.connector.redis.sink;

import com.sdu.streaming.warehouse.connector.redis.NoahArkRedisRuntimeConverter;
import com.sdu.streaming.warehouse.connector.redis.entry.NoahArkRedisData;
import com.sdu.streaming.warehouse.utils.MoreFutures;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.util.concurrent.ExecutorThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;


public class NoahArkRedisSinkFunction<T> extends RichSinkFunction<T> implements CheckpointedFunction {

    private static final Logger LOG = LoggerFactory.getLogger(NoahArkRedisSinkFunction.class);

    private final NoahArkRedisWriteOptions writeOptions;
    private final NoahArkRedisRuntimeConverter<T> converter;

    // 非集群模式
    private transient RedisClient client;
    private transient StatefulRedisConnection<byte[], byte[]> connection;

    private transient NoahArkRedisBufferQueue<NoahArkRedisData<?>> bufferQueue;

    // async write
    private transient ScheduledExecutorService executor;
    private transient ScheduledFuture scheduledFuture;

    // sync write
    private transient long latestFlushTimestamp;

    private transient volatile boolean closed = false;

    private final AtomicReference<Throwable> failureThrowable = new AtomicReference<>();

    public NoahArkRedisSinkFunction(NoahArkRedisWriteOptions writeOptions, NoahArkRedisRuntimeConverter<T> converter) {
        this.writeOptions = writeOptions;
        this.converter = converter;
    }

    @Override
    public void open(Configuration configuration) throws Exception {
        LOG.info("task[{} / {}] start initialize redis connection",
                getRuntimeContext().getIndexOfThisSubtask(), getRuntimeContext().getNumberOfParallelSubtasks());
        converter.open();
        initialize();
        client = RedisClient.create(writeOptions.getClusterName());
        connection = client.connect(new ByteArrayCodec());
        connection.setAutoFlushCommands(false);
    }

    @Override
    public void initializeState(FunctionInitializationContext functionInitializationContext) throws Exception {
        // nothing to do
    }

    @Override
    public void snapshotState(FunctionSnapshotContext functionSnapshotContext) throws Exception {
        // flush buffer
        if (bufferQueue.bufferSize() != 0) {
            flush();
        }
    }

    @Override
    public void invoke(T value, Context context) throws Exception {
        checkErrorAndRethrow();
        bufferQueue.buffer(converter.serialize(value));
        checkIfTriggerBufferFlush();
    }

    private void flush() {
        try {
            bufferQueue.flush(this::doFlush);
        } catch (Exception e) {
            failureThrowable.compareAndSet(null, e);
        }
        checkErrorAndRethrow();
    }

    private void doFlush(List<NoahArkRedisData<?>> bufferData) {
        // AsyncCommand + FlushCommands --> Redis Pipeline
        final List<RedisFuture<?>> result = new LinkedList<>();
        bufferData.forEach(redisData -> result.addAll(redisData.save(connection)));
        connection.flushCommands();
        MoreFutures.tryAwait(result);
    }

    private void checkErrorAndRethrow() {
        Throwable cause = failureThrowable.get();
        if (cause != null) {
            throw new RuntimeException("an error occurred in RedisSink.", cause);
        }
    }

    @Override
    public void close() throws Exception {
        closed = true;
        if (client != null) {
            client.shutdownAsync();
            connection.closeAsync();
        }
        if (writeOptions.isAsyncWrite() && scheduledFuture != null) {
            scheduledFuture.cancel(false);
            if (executor != null) {
                executor.shutdownNow();
            }
        }
    }

    private void initialize() {
        if (writeOptions.isAsyncWrite()) {
            bufferQueue = new NoahArkRedisAsyncBufferQueue<>();
            executor = Executors.newScheduledThreadPool(1, new ExecutorThreadFactory("redis-sink-flusher"));
            scheduledFuture = executor.scheduleWithFixedDelay(
                    () -> {
                        if (closed) {
                            return;
                        }
                        try {
                            flush();
                        } catch (Exception e) {
                            // fail the sink and skip the rest of the items
                            // if the failure handler decides to throw an exception
                            failureThrowable.compareAndSet(null, e);
                        }
                    },
                    writeOptions.getBufferFlushInterval(),
                    writeOptions.getBufferFlushInterval(),
                    TimeUnit.SECONDS
            );
            return;
        }
        bufferQueue = new NoahArkRedisSyncBufferQueue<>();
        latestFlushTimestamp = System.currentTimeMillis();
    }

    private void checkIfTriggerBufferFlush() {
        if (bufferQueue.bufferSize() >= writeOptions.getBufferFlushMaxSize()) {
            flush();
            return;
        }
        if (!writeOptions.isAsyncWrite()) {
            long currentTimestamp = System.currentTimeMillis();
            long interval = currentTimestamp - latestFlushTimestamp;
            if (writeOptions.getBufferFlushInterval() <= interval) {
                flush();
            }
        }
    }
}