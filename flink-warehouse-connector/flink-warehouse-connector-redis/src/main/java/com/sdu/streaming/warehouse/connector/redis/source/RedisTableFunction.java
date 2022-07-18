package com.sdu.streaming.warehouse.connector.redis.source;

import com.sdu.streaming.warehouse.connector.redis.RedisRuntimeConverter;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import org.apache.flink.shaded.curator5.com.google.common.cache.Cache;
import org.apache.flink.shaded.curator5.com.google.common.cache.CacheBuilder;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.functions.FunctionContext;
import org.apache.flink.table.functions.TableFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class RedisTableFunction extends TableFunction<RowData> {

    private static final Logger LOG = LoggerFactory.getLogger(RedisTableFunction.class);

    private final RedisReadOptions readOptions;

    private RedisRuntimeConverter<RowData> converter;

    private transient RedisClient client;
    private transient StatefulRedisConnection<byte[], byte[]> connection;
    private transient Cache<RowData, RowData> cache;

    public RedisTableFunction(RedisRuntimeConverter<RowData> converter, RedisReadOptions readOptions) {
        this.converter = converter;
        this.readOptions = readOptions;
    }

    @Override
    public void open(FunctionContext context) throws Exception {
        converter.open();
        client = RedisClient.create(readOptions.getClusterAddress());
        connection = client.connect(new ByteArrayCodec());
        connection.setAutoFlushCommands(true);
        if (readOptions.isCacheable() && readOptions.getCacheMaxSize() != -1 && readOptions.getCacheExpireMs() != -1) {
            cache = CacheBuilder.newBuilder()
                    .expireAfterWrite(readOptions.getCacheExpireMs(), TimeUnit.MILLISECONDS)
                    .build();
        }
    }

    public void eval(Object ... keys) {
        RowData keyRow = GenericRowData.of(keys);
        if (cache != null) {
            RowData cacheRow = cache.getIfPresent(keyRow);
            if (cacheRow != null) {
                collect(cacheRow);
                return;
            }
        }
        for (int retry = 0; retry <= readOptions.getMaxRetryTimes(); ++retry) {
            try {
                RowData rowData = converter.deserialize(connection, keyRow);
                if (cache == null && rowData != null) {
                    collect(rowData);
                    break;
                }
                if (cache != null && rowData != null) {
                    cache.put(keyRow, rowData);
                    collect(rowData);
                    break;
                }
            } catch (Exception e) {
                LOG.error("failed got data from redis, retry times: {}", retry, e);
                if (retry >= readOptions.getMaxRetryTimes()) {
                    throw new RuntimeException("failed got data from redis.", e);
                }
                try {
                    Thread.sleep(1000 * retry);
                } catch (InterruptedException e1) {
                    throw new RuntimeException(e1);
                }
            }
        }
    }


    @Override
    public void close() throws Exception {
        if (client != null) {
            client.shutdown();
        }
        if (connection != null) {
            connection.close();
        }
    }
}
