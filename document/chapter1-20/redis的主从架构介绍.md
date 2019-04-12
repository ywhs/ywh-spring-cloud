# redis的主从架构

在一个项目中使用redis必然是因为我们想要做成高并发，redis是整个大型的缓存架构中，支撑高并发的非常重要的一个环节，
但是光redis是不够的。

redis不能支撑高并发的瓶颈就是 **单机** 单机的redis几乎不可能说QPS超过10，除非你的机器性能特别好，维护做的好，
而且你的整体的操作不能太复杂。

所以那么读写分离一般来说是用来支撑高并发，写的请求比较少，大量的请求都是读，那么redis的主从架构就是比较好的选择，
主从架构 -> 读写分离 -> 水平扩容支撑读高并发，主节点用来写，从节点用来读。

![redis-master-slave](https://user-images.githubusercontent.com/34649300/55704596-197ad380-5a0f-11e9-99bd-f94aad99b8c4.png)

## redis replication（主从复制）的核心机制

- redis采用异步方式复制数据到slave节点，不过redis 2.8开始，slave node会周期性地确认自己每次复制的数据量
- 一个master node是可以配置多个slave node的
- slave node也可以连接其他的slave node
- slave node做复制的时候，是不会block master node的正常工作的
- slave node在做复制的时候，也不会block对自己的查询操作，它会用旧的数据集来提供服务; 但是复制完成的时候，需要删除旧数据集，加载新数据集，这个时候就会暂停对外服务了
- slave node主要用来进行横向扩容，做读写分离，扩容的slave node可以提高读的吞吐量


## redis 主从复制的核心原理

当启动一个 slave node 的时候，它会发送一个 `PSYNC` 命令给 master node。


如果 slave node 使用 `PSYNC` 初次连接到 master node，那么会触发一次 full resynchronization 全量复制。此时 master 会启动一个后台线程，
开始生成一份 RDB 快照文件，同时还会将从客户端 client 新收到的所有写命令缓存在内存中。RDB 文件生成完毕后， master 会将这个 RDB 发送给 slave，
slave 会先写入本地磁盘，然后再从本地磁盘加载到内存中，接着 master 会将内存中缓存的写命令发送到 slave，slave 也会同步这些数据。slave node 
如果跟 master node 有网络故障，断开了连接，会自动重连，连接之后 master node 仅会复制给 slave 部分缺少的数据。

![流程图](https://user-images.githubusercontent.com/34649300/55709361-b8f19380-5a1a-11e9-9de5-7eaacf827dcc.jpg)

### 什么是PSYNC命令呢 

- [PSYNC的介绍与详解](http://www.hulkdev.com/posts/redis_new_psync)

简单来说PSYNC完成的使命就是判断从服务器是否需要全量复制还是增量复制，毕竟每次因为一点点网路波动重新连接主节点，都要全量复制，非常的低效

PSYNC格式：
> PSYNC runid offset

**runid**

每个Redis服务器都会有一个表明自己身份的ID。在PSYNC中发送的这个ID是指之前连接的Master的ID，如果没保存这个ID，
PSYNC的命令会使用”PSYNC ? -1” 这种形式发送给Master，表示需要全量复制。

**offset（复制偏移量）**

在主从复制的Master和Slave双方都会各自维持一个offset。Master成功发送N个字节的命令后会将Master的offset加上N，
Slave在接收到N个字节命令后同样会将Slave的offset增加N。Master和Slave如果状态是一致的那么它的的offset也应该是一致的。

**流程、原理**

### 全量复制流程

- 主节点master收到全量复制的命令后 执行 bgsave ，在本地生成一份 rdb 快照文件;并使用一个缓冲区（称为复制缓冲区）记录从现在开始执行的所有写命令
redis配置文件中有个参数：client-output-buffer-limit slave 256MB 64MB 60

> 如果在复制期间，内存缓冲区超过60秒一直消耗超过 64MB，或者一次性超过 256MB，那么停止复制，复制失败。后两个参数是配合使用的，假如：消耗超过64MB
一直持续了59秒，但是60秒的时候不超过64MB了，那么就保持连接继续复制。

- master node 将 rdb 快照文件发送给 slave node，如果 rdb 复制时间超过 60秒（redis配置文件参数：repl-timeout），那么 slave node 就会认为复制失败，
可以适当调大这个参数(对于千兆网卡的机器，一般每秒传输 100MB，6G 文件，很可能超过 60s)

- master node 在生成 rdb 时，会将所有新的写命令缓存在内存中，在 slave node 保存了 rdb 之后，再将新的写命令复制给 slave node;
从节点首先清除自己的旧数据，然后载入接收的RDB文件

- 主节点将前述复制缓冲区中的所有写命令发送给从节点，从节点执行这些写命令，如果从节点开启了AOF，则会触发bgrewriteaof的执行，从而保证AOF文件更新至主节点的最新状态

### 部分复制流程（增量复制）

- 如果全量复制过程中，master-slave 网络连接断掉，那么 slave 重新连接 master 时，会触发增量复制。

- master 直接从自己的 backlog 中获取部分丢失的数据，发送给 slave node，默认 backlog 就是 1MB。

- master 就是根据 slave 发送的 psync 中的 offset 来从 backlog 中获取数据的。

> 例如，如果主节点的offset是1000，而从节点的offset是500，那么部分复制就需要将offset为501-1000的数据传递给从节点

### 完整的复制流程

slave node 启动时，会在自己本地保存 master node 的信息，包括 master node 的host和ip，但是复制流程没开始。

slave node 内部有个定时任务，每秒检查是否有新的 master node 要连接和复制，如果发现，就跟 master node 建立 socket 网络连接；
然后 slave node 发送 ping 命令给 master node。如果 master 设置了 requirepass（就是redis的登录密码），
那么 slave node 必须发送 masterauth 的口令过去进行认证；master node 第一次执行全量复制，将所有数据发给 slave node；
而在后续，master node 持续将写命令，异步复制给 slave node。

![redis-master-slave-replication-detail](https://user-images.githubusercontent.com/34649300/56006586-ef275f80-5d07-11e9-95a3-a95722279979.png)

- 第一步：从节点服务器内部维护了两个字段，即masterhost和masterport字段，用于存储主节点的ip和port信息。

- 第二步：建立socket连接，即从节点每秒1次调用复制定时函数replicationCron()，如果发现了有主节点可以连接，便会根据主节点的ip和port，创建socket连接

- 第三步：身份验证，如果从节点中设置了masterauth选项，则从节点需要向主节点进行身份验证；没有设置该选项，则不需要验证；
从节点进行身份验证是通过向主节点发送auth命令进行的，auth命令的参数即为配置文件中的masterauth的值；如果主节点设置密码的状态，
与从节点masterauth的状态一致（一致是指都存在，且密码相同，或者都不存在），则身份验证通过，复制过程继续；如果不一致，则从节点断开socket连接，并重连。

- 第四步：数据同步，就是全量复制或者增量复制，而且在复制阶段继续有写命令会存在主节点内存中，后续会异步发送给slave node

slave node如果跟master node有网络故障，断开了连接，会自动重连。master如果发现有多个slave node都来重新连接，
仅仅会启动一个rdb save操作，用一份数据服务所有slave node。

## 主从复制的断点续传

从redis 2.8开始，就支持主从复制的断点续传，如果主从复制过程中，网络连接断掉了，那么可以接着上次复制的地方，继续复制下去，而不是从头开始复制一份

master node会在内存中常见一个backlog，master和slave都会保存一个replica offset还有一个master id，offset就是保存在backlog中的。
如果master和slave网络连接断掉了，slave会让master从上次的replica offset开始继续复制

但是如果没有找到对应的offset，那么就会执行一次resynchronization（全量复制）

## 无磁盘化复制

master 在内存中直接创建 RDB，然后发送给 slave，不会在自己本地落地磁盘了。只需要在配置文件中开启 repl-diskless-sync yes 即可。

```bash
repl-diskless-sync yes

# 等待 5s 后再开始复制，因为要等更多 slave 重新连接过来
repl-diskless-sync-delay 5
```

## 过期key处理

slave不会过期key，只会等待master过期key。如果master过期了一个key，或者通过LRU淘汰了一个key，那么会模拟一条del命令发送给slave。

## heartbeat

主从节点互相都会发送 heartbeat 信息。

master 默认每隔 10秒 发送一次 heartbeat，slave node 每隔 1秒 发送一个 heartbeat。

## master持久化对于主从架构的安全保障的意义

***如果采用了主从架构，那么建议必须开启master node的持久化！*** [如何实现redis的持久化](https://github.com/ywhs/YwhSpringCloud/blob/master/document/chapter1-20/%E5%A6%82%E4%BD%95%E5%AE%9E%E7%8E%B0redis%E7%9A%84%E6%8C%81%E4%B9%85%E5%8C%96.md)

而且不建议使用slave node作为master node的数据热备如果你关掉master的持久化，可能在master宕机重启的时候数据是空的，然后可能一经过复制，salve node数据也丢了

另外，master 的各种备份方案，也需要做。万一本地的所有文件丢失了，从备份中挑选一份 rdb 去恢复 master，这样才能确保启动的时候，是有数据的
即使采用了后续讲解的高可用机制，slave node可以自动接管master node，但是也可能sentinal还没有检测到master failure，master node就自动重启了，
还是可能导致上面的所有slave node数据清空故障。


## 扩展阅读

- [Redis 主从架构](https://github.com/doocs/advanced-java/blob/master/docs/high-concurrency/redis-master-slave.md)

- [深入学习Redis（3）：主从复制](https://www.cnblogs.com/kismetv/p/9236731.html#t41)










