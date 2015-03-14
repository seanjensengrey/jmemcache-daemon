# jmemcached #

This is a Java implementation of the daemon (server) side of the [memcached](http://www.danga.com/memcached/) protocol.

Memcache is a network accessible key/value storage system, often used as a distributed cache.

Jmemcached is functionally equivalent in most respects to (native code) memcached, but is written in pure Java, and is thus suitable for:

  * Portable or Java-only distributions of applications
  * Embedding inside applications
  * Integration testing

## What would I use it for? ##

A distributed client/server style cache. Some people use memcached to reduce load on their database. Some people just use it to share data across a cluster. It's simple and common. It's a good general purpose network available cache to compliment any network available service.

## Tell me more details... ##

Ok.

  * It is implemented in, and requires at least Java 5.
  * It is protocol compatible with the C version of memcached.  Existing clients, including the ones for Java ([1](http://www.whalin.com/memcached/) & [2](http://code.google.com/p/spymemcached/)), should work without modification.  Replacing the Java version with the C version (and vice versa) is trivial.
  * Jmemcached supports both the ASCII and binary memcache protocols.
  * It can be embedded in your existing Java project. (For example hosted by a web application or by an OSGI bundle.)
  * ... or it can be used from the command line interface, with commands roughly switch compatible with the C version.
  * ... or it can be configured using your favourite dependency injection framework (like Spring, PicoContainer, etc.)
  * It uses [JBoss Netty](http://www.jboss.org/netty/) for non-blocking, scalable (Java NIO) network I/O.
  * While it is [slower](PerformanceMeasurements.md) than the C version, it is still quite fast and suitable for most applications.
  * The cache portion of the project can be used independently of the daemon so that local process users can have quick low-overhead access to the cache while maintaining the client-server relationship for external clients.
  * The storage for the cache is abstracted so it should be possible to replace it with other cache implementations (such as OSCache, EHCache, etc.) if that is appropriate.
  * Cache storage can be handled either by the Java (garbage collected) heap, or, if more storage is required, in a separate memory-mapped file.

## How do I use it? ##

There are two ways; programmatically (embedded inside your application) or using the command-line (CLI) interface.

For programmatic example, here's a snippet from the source for the main class for the CLI:

```
        // create daemon and start it
        final MemCacheDaemon<LocalCacheElement> daemon = new MemCacheDaemon<LocalCacheElement>();

        CacheStorage<Key, LocalCacheElement> storage = ConcurrentLinkedHashMap.create(ConcurrentLinkedHashMap.EvictionPolicy.FIFO, maxItems, maxBytes);
        daemon.setCache(new CacheImpl(storage));
        daemon.setBinary(binary);
        daemon.setAddr(addr);
        daemon.setIdleTime(idle);
        daemon.setVerbose(verbose);
        daemon.start();
```

To use ''jmemcached'' from the command line, just run Java against the cli "-with-dependendencies" JAR.

The CLI accepts the following options:

```
 -b,--binary              binary protocol mode
 -bs,--block-size <arg>   block size (in bytes) for external memory mapped
                          file allocator.  default is 8 bytes
 -c,--ceiling <arg>       ceiling memory to use; in bytes, specify K, kb,
                          M, GB for larger units
 -f,--mapped-file <arg>   use external (from JVM) heap through a memory
                          mapped file
 -h,--help                print this help screen
 -i,--idle <arg>          disconnect after idle <x> seconds
 -l,--listen <arg>        Address to listen on
 -m,--memory <arg>        max memory to use; in bytes, specify K, kb, M,
                          GB for larger units
 -p,--port <arg>          port to listen on
 -s,--size <arg>          max items
 -v                       verbose (show commands)
 -V                       Show version number
```


## How can I get it? ##

To make use of jmemcached in your project, the easiest way is to use Maven 2.  jmemcached artifacts are available in Maven 2 central.

And then add the dependency:
```
    <dependency>
        <groupId>com.thimbleware.jmemcached</groupId>
        <artifactId>jmemcached-core</artifactId>
        <version>1.0.0</version>
    </dependency>
```

If you are not using Maven, you can retrieve the compiled JARs directly via HTTP from the Maven 2 central repository at http://repo2.maven.org/maven2/com/thimbleware/jmemcached/

Debian/Ubuntu packages are also available in the repository in the jmemcached-cli

## What's next? ##

Missing features (when compared against the native [C](C.md) version):

  * delayed 'flush\_all'
  * UDP protocol

## Thanks to... ##

`YourKit` is kindly supporting open source projects with its full-featured Java Profiler.
`YourKit`, LLC is the creator of innovative and intelligent tools for profiling
Java and .NET applications. Take a look at `YourKit`'s leading software products:
[YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp) and
[YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp).