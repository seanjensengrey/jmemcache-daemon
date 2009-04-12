package com.thimbleware.jmemcached.storage.zookeeper;

import com.thimbleware.jmemcached.MCElement;
import com.thimbleware.jmemcached.storage.CacheStorage;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.WatchedEvent;

import java.io.IOException;
import java.util.Set;

/**
 * Simple memcached storage engine built around ZooKeeper clusters
 *
 * Behaves something like an ordered version of Kestrel
 */
public class ZooKeeperQueueCacheStorage implements CacheStorage, Watcher {

    private ZooKeeper zooKeeper;

    public ZooKeeperQueueCacheStorage(String hostname, int port) {

        try {
            zooKeeper = new ZooKeeper(hostname + ":" + port, 500, this);
        } catch (IOException e) {
            zooKeeper = null;
            throw new RuntimeException(e);
        }

    }


    public MCElement get(String keystring) {
        Get get = new Get(keystring).invoke();

        String key = get.getKey();
        int timeout = get.getTimeout();

        // manufacture the queue
        Queue queue = new Queue(zooKeeper, "/" + key);

        try {
            return queue.consume(timeout, timeout != 0);
        } catch (KeeperException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void put(String keystring, MCElement el, int data_length) {
        Queue queue = new Queue(zooKeeper, "/" + keystring);
        try {
            queue.produce(el);
        } catch (KeeperException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void remove(String keystring) {
        Get get = new Get(keystring).invoke();

        String key = get.getKey();
        int timeout = get.getTimeout();

        // manufacture the queue
        Queue queue = new Queue(zooKeeper, "/" + key);

        try {
            queue.consume(timeout, timeout != 0);
        } catch (KeeperException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<String> keys() {
        throw new UnsupportedOperationException("keys operation not supported on a queue yet");
    }

    public void clear() {
        throw new UnsupportedOperationException("clear operation not supported on a queue yet");
    }

    public void close() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getCurrentSizeBytes() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getMaximumSizeBytes() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public long getCurrentItemCount() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public int getMaximumItems() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void process(WatchedEvent watchedEvent) {
        synchronized (Mutex.mutexValue) {
            Mutex.mutexValue.notify();

            System.err.println("...");
        }
    }

    private class Get {
        private String keystring;
        private String key;
        private int timeout;

        public Get(String keystring) {
            this.keystring = keystring;
        }

        public String getKey() {
            return key;
        }

        public int getTimeout() {
            return timeout;
        }

        public Get invoke() {
            key = keystring;
            timeout = 0;
            // parse timeout options
            if (keystring.contains("/")) {
                String[] options = keystring.split("/");
                key = options[0];
                for (String option : options) {
                    if (option.startsWith("t="))
                        timeout = Integer.parseInt(option.substring(2));
                }

            }
            return this;
        }
    }
}
