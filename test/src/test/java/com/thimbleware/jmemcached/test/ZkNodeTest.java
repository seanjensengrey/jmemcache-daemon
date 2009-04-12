package com.thimbleware.jmemcached.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.File;
import java.net.InetSocketAddress;
import java.util.Arrays;

import net.spy.memcached.MemcachedClient;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.apache.mina.util.AvailablePortFinder;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.apache.zookeeper.server.NIOServerCnxn;

import com.thimbleware.jmemcached.Cache;
import com.thimbleware.jmemcached.MemCacheDaemon;
import com.thimbleware.jmemcached.storage.zookeeper.ZooKeeperQueueCacheStorage;

/**
 * Test basic functionality of Spy Memcached client 2.3 to JMemcached
 * seee http://thimbleware.com/projects/jmemcached/ticket/1
 * @author martin.grotzke@javakaffee.de
 */
public class ZkNodeTest {

    private MemCacheDaemon _daemon;
    private MemcachedClient _client;
    private int mcDaemonPort;
    private int zkDaemonPort;

    private ZooKeeperServer server;

    private NIOServerCnxn.Factory cnxnFactory;

    @Before
    public void setUp() throws Exception {
        zkDaemonPort = AvailablePortFinder.getNextAvailable();


        File zkDir = new File("target/zk");
        
        server = new ZooKeeperServer(zkDir, zkDir, 10);
        server.startup();

        cnxnFactory = new NIOServerCnxn.Factory(zkDaemonPort);
        cnxnFactory.setZooKeeperServer(server);

        System.err.println("starting connection factory for port: " + zkDaemonPort);
        
        // get the conn factor running in another thread
        new Thread(new Runnable() {

            public void run() {
                try {
                    cnxnFactory.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();


        mcDaemonPort = AvailablePortFinder.getNextAvailable();

        final InetSocketAddress address = new InetSocketAddress( "localhost", mcDaemonPort);
        _daemon = createDaemon( address );
        _daemon.start(); // hello side effects

        _client = new MemcachedClient( Arrays.asList( address ) );
    }

    @After
    public void tearDown() throws Exception {
        _daemon.stop();
        server.shutdown();
        cnxnFactory.shutdown();
    }

    @Test
    public void testPresence() {
        assertNotNull(_daemon.getCache());
        assertEquals("initial cache is empty", 0, _daemon.getCache().getCurrentItems());
        assertEquals("initialize size is empty", 0, _daemon.getCache().getCurrentBytes());
    }

    @Test
    public void testSimpleSetGet() throws IOException, InterruptedException {
        _client.set( "foo", 5000, "bar" );
        Assert.assertEquals( "bar", _client.get( "foo/t=500" ) );
    }


    @Test
    public void testBulkOrderedSetGet() throws IOException, InterruptedException {
        // dump 1000 items in, then retrieve them, and the order should be correct
        for (int i = 0; i < 1000; i++) {
            _client.set( "foo" + i, 0, "bar" + i );
        }
        for (int i = 0; i < 1000; i++) {
            Assert.assertEquals( "bar" + i, _client.get( "foo" + i + "/t=500" + i) );
        }
    }
    
    @Test
    public void testBulkOrderedQueueDequeue() throws IOException, InterruptedException {
        // dump 1000 items in, then retrieve them, and the order should be correct
        for (int i = 0; i < 1000; i++) {
            _client.set( "foo", 0, "bar" + i );
        }
        for (int i = 0; i < 1000; i++) {
            Assert.assertEquals( "bar" + i, _client.get( "foo" ) );
        }
    }

    private MemCacheDaemon createDaemon( final InetSocketAddress address ) throws IOException {
        final MemCacheDaemon daemon = new MemCacheDaemon();
        final ZooKeeperQueueCacheStorage cacheStorage = new ZooKeeperQueueCacheStorage("localhost", zkDaemonPort);
        daemon.setCache(new Cache(cacheStorage));
        daemon.setAddr( address );
        daemon.setVerbose(true);
        return daemon;
    }

}