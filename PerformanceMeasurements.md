# Details #

The following test is based on running the following script:

```
for x in [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30]; do 
   memslap --servers=localhost --tcp-nodelay --concurrency=$x --flush --test=get; 
done
```

With two different configurations:

  * memcached (1.4.1)
  * jmemcached with ConcurrentLinkedHashMap

Memory/GC settings for jmemcached's jvm: `-Xms512m -Xmx2048m -XX:PermSize=256m -XX:MaxPermSize=2048m -d64`

Physical machine is a 2.4ghz Dual Core iMac.

JVM info is
```
java version "1.6.0_16"
Java(TM) SE Runtime Environment (build 1.6.0_16-b01)
Java HotSpot(TM) Server VM (build 14.2-b01, mixed mode)
```


# get performance #

![http://jmemcache-daemon.googlecode.com/hg/misc/memslap-get-perf.png](http://jmemcache-daemon.googlecode.com/hg/misc/memslap-get-perf.png)

# summary #

The sparse documentation on memslap doesn't give much insight into exactly what it is testing. From reading the source it appears to be that it is:

  * loading 10000 items into memcache
  * gets all 10000 items, in N # of concurrent threads

The numbers here seem to show that jmemcached scales to multiple threads fairly well under, almost as well as memcached.  That is to say that as the # of threads hitting the cache increases, the response times decrease in a fairly linear fashion and in a manner that is not dissimilar from the way memcached performance degrades.

However, it also shows there is a constant overhead above the native memcached that makes it about 50% as efficient in overall throughput.

(The lack of a good comprehensive memcached stress/performance testing tool is a problem here. Something will have to be put together to get more representative numbers.)

It is unclear to me (from profiling & testing) if the performance differences lay inside the cache get itself (seems not to be the case), or if it's protocol/netty related. I have some strong evidence that suggests the latter.