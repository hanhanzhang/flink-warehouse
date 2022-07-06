package com.sdu.streaming.warehouse.connector.redis.entry;

import java.util.Map;

import com.sdu.streaming.warehouse.connector.redis.NoahArkAbstractRedisObject;
import org.apache.flink.types.RowKind;

public class NoahArkRedisMapObject extends NoahArkAbstractRedisObject {

    private final Map<byte[], byte[]> values;

    public NoahArkRedisMapObject(RowKind kind, byte[] keys, Map<byte[], byte[]> values) {
        super(kind, keys);
        this.values = values;
    }

    @Override
    public Map<byte[], byte[]> getRedisValueAsMap() {
        return values;
    }
}
