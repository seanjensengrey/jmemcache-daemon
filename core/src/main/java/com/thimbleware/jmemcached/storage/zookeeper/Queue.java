package com.thimbleware.jmemcached.storage.zookeeper;

import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.*;

import java.util.List;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;

import com.thimbleware.jmemcached.MCElement;

/**
 * Producer-Consumer queue
 */
public class Queue {

    public static final String ROOT = "/queues";

    private String queueName;
    private ZooKeeper zooKeeper;


    public Queue(ZooKeeper zooKeeper, String queueName) {
        this.zooKeeper = zooKeeper;
        this.queueName = queueName;
        
        // establish the queue
        try {
            Stat stat = zooKeeper.exists(queueName, false);
            if (stat == null) {
                String path = zooKeeper.create(queueName , new byte[] {}, ZooDefs.Ids.OPEN_ACL_UNSAFE,
                        CreateMode.PERSISTENT);

            }
        } catch (KeeperException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    boolean produce(MCElement i) throws KeeperException, InterruptedException, IOException {
        ByteArrayOutputStream bbs = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bbs);
        oos.writeObject(i);
        oos.close();

        zooKeeper.create(queueName + "/element", bbs.toByteArray(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL, new AsyncCallback.StringCallback() {
            public void processResult(int i, String s, Object o, String s1) {
                // noop
                System.err.println("submission complete");
            }
        }, i);

        return true;
    }

    MCElement consume(int waitTimeMs, boolean wait) throws KeeperException, InterruptedException, IOException, ClassNotFoundException {
        MCElement retvalue = null;
        Stat stat = null;

        // Get the first element available
        while (true) {
            synchronized (Mutex.mutexValue) {
                List<String> list = zooKeeper.getChildren(queueName, true);
                if (wait && list.size() == 0) {
                    try {
                        Mutex.mutexValue.wait(waitTimeMs);
                    } catch (InterruptedException e) {
                        return null;
                    }
                } else {
                    // sort the list, the min is the first.
                    Collections.sort(list);
                    String minKey = list.get(0);
                    byte[] b = zooKeeper.getData(queueName + "/" + minKey, false, stat);
                    zooKeeper.delete(queueName + "/" + minKey, 0);

                    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(b));
                    retvalue = (MCElement) ois.readObject();
                    ois.close();

                    return retvalue;
                }
            }
        }
    }


}
