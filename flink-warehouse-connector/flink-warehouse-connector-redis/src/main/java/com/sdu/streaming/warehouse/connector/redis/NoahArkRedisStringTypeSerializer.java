package com.sdu.streaming.warehouse.connector.redis;

import com.sdu.streaming.warehouse.deserializer.NoahArkDataDeserializer;
import com.sdu.streaming.warehouse.deserializer.NoahArkDataSerializer;
import com.sdu.streaming.warehouse.utils.NoahArkByteArrayDataInput;
import com.sdu.streaming.warehouse.utils.NoahArkByteArrayDataOutput;
import org.apache.flink.table.data.GenericRowData;
import org.apache.flink.table.data.RowData;
import org.apache.flink.util.Preconditions;

import java.io.IOException;

public class NoahArkRedisStringTypeSerializer extends NoahArkAbstractRedisTypeSerializer<byte[]> {

    public static final NoahArkRedisStringTypeSerializer REDIS_STRING_DESERIALIZER =
            new NoahArkRedisStringTypeSerializer();

    private NoahArkRedisStringTypeSerializer() { }

    @Override
    public byte[] serializeValue(RowData rowData, String[] fieldNames, RowData.FieldGetter[] rowFieldGetters, NoahArkDataSerializer[] rowFieldSerializers) throws IOException {
        Preconditions.checkArgument(rowFieldGetters.length == rowFieldSerializers.length);

        NoahArkByteArrayDataOutput out = new NoahArkByteArrayDataOutput();
        for (int pos = 0; pos < rowData.getArity(); ++pos) {
            Object fieldValue = rowFieldGetters[pos].getFieldOrNull(rowData);
            rowFieldSerializers[pos].serializer(fieldValue, out);
        }
        return out.toByteArray();
    }

    @Override
    public RowData deserializeValue(byte[] bytes, String[] fieldNames, NoahArkDataDeserializer[] rowFieldDeserializers) throws IOException {
        Preconditions.checkArgument(fieldNames.length == rowFieldDeserializers.length);

        GenericRowData rowData = new GenericRowData(fieldNames.length);
        NoahArkByteArrayDataInput input = new NoahArkByteArrayDataInput(bytes);
        for (int pos = 0; pos < fieldNames.length; ++pos) {
            Object fieldValue = rowFieldDeserializers[pos].deserializer(input);
            rowData.setField(pos, fieldValue);
        }
        return rowData;
    }

}
