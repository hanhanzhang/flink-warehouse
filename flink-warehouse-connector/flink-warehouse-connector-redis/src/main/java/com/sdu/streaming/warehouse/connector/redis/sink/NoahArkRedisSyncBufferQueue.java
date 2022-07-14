package com.sdu.streaming.warehouse.connector.redis.sink;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class NoahArkRedisSyncBufferQueue<T> implements NoahArkRedisBufferQueue<T>  {

    private List<T> queue;

    public NoahArkRedisSyncBufferQueue() {
        this.queue = new LinkedList<>();
    }

    @Override
    public synchronized long bufferSize() {
        return queue.size();
    }

    @Override
    public synchronized void buffer(T data) {
        queue.add(data);
    }

    @Override
    public synchronized void flush(Consumer<List<T>> flusher) {
        flusher.accept(queue);
        queue.clear();
    }
}
