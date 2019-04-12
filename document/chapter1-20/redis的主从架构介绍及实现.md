# redis的主从架构

在一个项目中使用redis必然是因为我们想要做成高并发，redis是整个大型的缓存架构中，支撑高并发的非常重要的一个环节，
但是光redis是不够的。

redis不能支撑高并发的瓶颈就是 **单机** 单机的redis几乎不可能说QPS超过10，除非你的机器性能特别好，维护做的好，
而且你的整体的操作不能太复杂。

所以那么读写分离一般来说是用来支撑高并发，写的请求比较少，大量的请求都是读，那么redis的主从架构就是比较好的选择，
主从架构 -> 读写分离 -> 水平扩容支撑读高并发，主节点用来写，从节点用来读。

> PS:在我使用的5.0.4版本中slave这个术语已经被replica代替了，嗯~  这个单词的意思确实有点不好听哈。反正官方已经改掉了，我也就全换掉了。

- 有关链接：[Changing Redis master-slave replication terms with something else](https://github.com/antirez/redis/issues/5335)

![master-replica](https://user-gold-cdn.xitu.io/2019/4/12/16a10c8dff9842bd?w=648&h=441&f=png&s=16342)

## redis replication（主从复制）的核心机制

- redis采用异步方式复制master节点数据到replica节点，不过redis 2.8开始，replica node会周期性地确认自己每次复制的数据量
- 一个master node是可以配置多个replica node的
- replica node也可以连接其他的replica node
- replica node做复制的时候，是不会block master node的正常工作的
- replica node在做复制的时候，也不会block对自己的查询操作，它会用旧的数据集来提供服务; 但是复制完成的时候，需要删除旧数据集，加载新数据集，这个时候就会暂停对外服务了
- replica node主要用来进行横向扩容，做读写分离，扩容的replica node可以提高读的吞吐量


## redis 主从复制的核心原理

当启动一个 replica node 的时候，它会发送一个 `PSYNC` 命令给 master node。


如果 replica node 使用 `PSYNC` 初次连接到 master node，那么会触发一次 full resynchronization 全量复制。此时 master 会启动一个后台线程，
开始生成一份 RDB 快照文件，同时还会将从客户端 client 新收到的所有写命令缓存在内存中。RDB 文件生成完毕后， master 会将这个 RDB 发送给 replica，
replica 会先写入本地磁盘，然后再从本地磁盘加载到内存中，接着 master 会将内存中缓存的写命令发送到 replica，replica 也会同步这些数据。replica node 
如果跟 master node 有网络故障，断开了连接，会自动重连，连接之后 master node 仅会复制给 replica 部分缺少的数据。

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

在主从复制的Master和replica双方都会各自维持一个offset。Master成功发送N个字节的命令后会将Master的offset加上N，
replica在接收到N个字节命令后同样会将replica的offset增加N。Master和replica如果状态是一致的那么它的的offset也应该是一致的。

### 全量复制流程

- 主节点master收到全量复制的命令后 执行 bgsave ，在本地生成一份 rdb 快照文件;并使用一个缓冲区（称为复制缓冲区）记录从现在开始执行的所有写命令
redis配置文件中有个参数：client-output-buffer-limit replica 256MB 64MB 60

> 如果在复制期间，内存缓冲区超过60秒一直消耗超过 64MB，或者一次性超过 256MB，那么停止复制，复制失败。后两个参数是配合使用的，假如：消耗超过64MB
一直持续了59秒，但是60秒的时候不超过64MB了，那么就保持连接继续复制。

- master node 将 rdb 快照文件发送给 replica node，如果 rdb 复制时间超过 60秒（redis配置文件参数：repl-timeout），那么 replica node 就会认为复制失败，
可以适当调大这个参数(对于千兆网卡的机器，一般每秒传输 100MB，6G 文件，很可能超过 60s)

- master node 在生成 rdb 时，会将所有新的写命令缓存在内存中，在 replica node 保存了 rdb 之后，再将新的写命令复制给 replica node;
从节点首先清除自己的旧数据，然后载入接收的RDB文件

- 主节点将前述复制缓冲区中的所有写命令发送给从节点，从节点执行这些写命令，如果从节点开启了AOF，则会触发bgrewriteaof的执行，从而保证AOF文件更新至主节点的最新状态

### 部分复制流程（增量复制）

- 如果全量复制过程中，master-replica 网络连接断掉，那么 replica 重新连接 master 时，会触发增量复制。

- master 直接从自己的 backlog 中获取部分丢失的数据，发送给 replica node，默认 backlog 就是 1MB。

- master 就是根据 replica 发送的 psync 中的 offset 来从 backlog 中获取数据的。

> 例如，如果主节点的offset是1000，而从节点的offset是500，那么部分复制就需要将offset为501-1000的数据传递给从节点

### 完整的复制流程

replica node 启动时，会在自己本地保存 master node 的信息，包括 master node 的host和ip，但是复制流程没开始。

replica node 内部有个定时任务，每秒检查是否有新的 master node 要连接和复制，如果发现，就跟 master node 建立 socket 网络连接；
然后 replica node 发送 ping 命令给 master node。如果 master 设置了 requirepass（就是redis的登录密码），
那么 replica node 必须发送 masterauth 的口令过去进行认证；master node 第一次执行全量复制，将所有数据发给 replica node；
而在后续，master node 持续将写命令，异步复制给 replica node。

![redis-master-slave-replication-detail](https://user-images.githubusercontent.com/34649300/56006586-ef275f80-5d07-11e9-95a3-a95722279979.png)

- 第一步：从节点服务器内部维护了两个字段，即masterhost和masterport字段，用于存储主节点的ip和port信息。

- 第二步：建立socket连接，即从节点每秒1次调用复制定时函数replicationCron()，如果发现了有主节点可以连接，便会根据主节点的ip和port，创建socket连接

- 第三步：身份验证，如果从节点中设置了masterauth选项，则从节点需要向主节点进行身份验证；没有设置该选项，则不需要验证；
从节点进行身份验证是通过向主节点发送auth命令进行的，auth命令的参数即为配置文件中的masterauth的值；如果主节点设置密码的状态，
与从节点masterauth的状态一致（一致是指都存在，且密码相同，或者都不存在），则身份验证通过，复制过程继续；如果不一致，则从节点断开socket连接，并重连。

- 第四步：数据同步，就是全量复制或者增量复制，而且在复制阶段继续有写命令会存在主节点内存中，后续会异步发送给replica node

replica node如果跟master node有网络故障，断开了连接，会自动重连。master如果发现有多个replica node都来重新连接，
仅仅会启动一个rdb save操作，用一份数据服务所有replica node。

## 主从复制的断点续传

从redis 2.8开始，就支持主从复制的断点续传，如果主从复制过程中，网络连接断掉了，那么可以接着上次复制的地方，继续复制下去，而不是从头开始复制一份

master node会在内存中常见一个backlog，master和replica都会保存一个replica offset还有一个master id，offset就是保存在backlog中的。
如果master和replica网络连接断掉了，replica会让master从上次的replica offset开始继续复制

但是如果没有找到对应的offset，那么就会执行一次resynchronization（全量复制）

## 无磁盘化复制

master 在内存中直接创建 RDB，然后发送给 replica，不会在自己本地落地磁盘了。只需要在配置文件中开启 repl-diskless-sync yes 即可。

```bash
repl-diskless-sync yes

# 等待 5s 后再开始复制，因为要等更多 replica 重新连接过来
repl-diskless-sync-delay 5
```

## 过期key处理

replica不会过期key，只会等待master过期key。如果master过期了一个key，或者通过LRU淘汰了一个key，那么会模拟一条del命令发送给replica。

## heartbeat

主从节点互相都会发送 heartbeat 信息。

master 默认每隔 10秒 发送一次 heartbeat，replica node 每隔 1秒 发送一个 heartbeat。

## master持久化对于主从架构的安全保障的意义

***如果采用了主从架构，那么建议必须开启master node的持久化！*** [如何实现redis的持久化](https://github.com/ywhs/YwhSpringCloud/blob/master/document/chapter1-20/%E5%A6%82%E4%BD%95%E5%AE%9E%E7%8E%B0redis%E7%9A%84%E6%8C%81%E4%B9%85%E5%8C%96.md)

而且不建议使用replica node作为master node的数据热备如果你关掉master的持久化，可能在master宕机重启的时候数据是空的，然后可能一经过复制，replica node数据也丢了

另外，master 的各种备份方案，也需要做。万一本地的所有文件丢失了，从备份中挑选一份 rdb 去恢复 master，这样才能确保启动的时候，是有数据的
即使采用了后续讲解的高可用机制，replica node可以自动接管master node，但是也可能sentinal还没有检测到master failure，master node就自动重启了，
还是可能导致上面的所有replica node数据清空故障。

## 实现redis的主从复制架构（一主一从）

ok，上面介绍了主从架构的相关知识和原理，下面我们开始进行实现以及实验。

|node |hostname  |IP  |port |
|--|--|--|--|
|master node |eshop-cache01 |192.168.0.30 |6379 |
|replica node |eshop-cache02 |192.168.0.31 |6379 |

- [环境搭建](https://github.com/ywhs/ywh-spring-cloud/blob/master/document/%E4%BA%BF%E7%BA%A7%E6%B5%81%E9%87%8F%E9%AB%98%E5%B9%B6%E5%8F%91%E9%AB%98%E5%8F%AF%E7%94%A8%E7%BC%93%E5%AD%98%E6%A1%86%E6%9E%B6%E6%90%AD%E5%BB%BA%E7%8E%AF%E5%A2%83.md)

在除了安装redis时配置的参数外，从节点还需要额外配置以下内容

## 配置主节点（eshop-cache01）配置文件

- 在配置文件中修改 `bind 127.0.0.1` 为 `bind 192.168.0.30` 自己本身的ip地址
> 或者配置成 bind 0.0.0.0 如果配置成本身ip地址，则需要在使用redis的客户端时，使用redis-cli -h 192.168.0.30进入客户端了

- 配置认证密码 `requirepass redis-pass` , 就是登录redis 的登录密码

## 配置从节点（eshop-cache02）配置文件

- 在配置文件中修改 `bind 127.0.0.1` 为 `bind 192.168.0.31` 自己本身的ip地址

- 配置 `replicaof` 为 `replicaof eshop-cache01 6379` eshop-cache01我是在 master node `/etc/hosts` 中配置了映射本机的ip
> replicaof <masterip> <masterport>  填写主节点的ip 和 端口号

- 开启安全认证 `masterauth redis-pass` 填写的就是 master node 的登录密码，需要在主节点配置文件中配置

- 强制读写分离 `replica-read-only yes` 这个默认是开启的 

可以自由选择是否开启AOF持久化

## 测试主从复制（读写分离）

先后启动主节点和从节点的redis实例，进入主节点客户端，`set k1 v1` 然后在从节点中看是否能读取到数据，读取到数据即代表成功。

![master-replica](https://user-images.githubusercontent.com/34649300/56023098-c15e0d00-5d3f-11e9-9efb-1e1bfba4d44e.gif)

## 扩展阅读

- [Redis 主从架构](https://github.com/doocs/advanced-java/blob/master/docs/high-concurrency/redis-master-slave.md)

- [深入学习Redis（3）：主从复制](https://www.cnblogs.com/kismetv/p/9236731.html#t41)

