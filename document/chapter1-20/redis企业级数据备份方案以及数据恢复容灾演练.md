# 企业级数据备份方案以及数据恢复演练

前两章笔记学习了简单的持久化原理和操作，但是在企业中，持久化到底是怎么样去使用的呢？

企业级的数据备份和各种灾难下的数据恢复，是怎么样做到的呢？


## 企业级的持久化的配置策略

在企业中，RDB的生成策略用默认的也差不多，默认中配置了三种方式,最有可能改的就是第三个了，前两个可以保持不变。
如果你希望尽可能确保说，RDB最多丢1分钟的数据，那么尽量就是每隔1分钟都生成一个快照

```bash
save 900 1
save 300 10
save 60 10000
```
而在一分钟内是10000->生成RDB，1000->RDB，这个根据你自己的应用和业务的数据量，你自己去决定，就是说一分钟内没有10000条数据更改他是不会生成
RDB文件的，所以要根据自己的业务量来配置这个策略。

AOF一定要打开，设置appendfsync everysec

auto-aof-rewrite-percentage 100: 就是当前AOF大小膨胀到超过上次100%，上次的两倍
auto-aof-rewrite-min-size 64mb: 根据你的数据量来定，16mb，32mb

## 企业级的数据备份方案

因为RDB非常适合做冷备，所以生成之后，应该存储一份备份，存储本机其他目录或者云服务器等等。

**数据备份方案**

- 编写脚本拷贝到其他目录下做备份
- 写crontab定时调度脚本去做数据备份
- 每小时都copy一份rdb的备份，到一个目录中去，仅仅保留最近48小时的备份
- 每天都保留一份当日的rdb的备份，到一个目录中去，仅仅保留最近1个月的备份
- 每次copy备份的时候，都把太旧的备份给删了
- 每天晚上将当前服务器上所有的数据备份，发送一份到远程的云服务上去

**编写每小时拷贝备份的脚本**

这个脚本我放到了/opt/eshop/redis/copy目录下，名字叫做**redis_rdb_copy_hourly.sh**可自己决定放到哪，但是要记住，别最后自己都找不到了。
```bash
#!/bin/sh

# 接收当前时间小时为间隔为变量，以防万一先删除以当前时间命名的文件夹，
# 再创建一个以当前时间命名的文件夹，然后拷贝rdb文件到这个文件中
cur_date=`date +%Y%m%d%k`
rm -rf /opt/eshop/redis/snapshotting/$cur_date
mkdir -p /opt/eshop/redis/snapshotting/$cur_date
cp /var/redis/6379/dump.rdb /opt/eshop/redis/snapshotting/$cur_date

# 接收一个48小时之前的变量，并且删除这个变量的文件
del_date=`date -d -48hour +%Y%m%d%k`
rm -rf /opt/eshop/redis/snapshotting/$del_date
```

编写liunx中的定时调度脚本，crontab -e

- [crontab详解](https://linuxtools-rst.readthedocs.io/zh_CN/latest/tool/crontab.html)


```bash
$ crontab -e
0 * * * * sh /opt/eshop/redis/copy/redis_rdb_copy_hourly.sh
```

上面表示每天每小时的0分开始执行后面的语句，具体的详情可参考上面的连接

**编写每天拷贝备份的脚本**

每天的脚本跟每小时的脚本只差了时间的变量上，后面不在跟着%k而已,取脚本文件名字叫做**redis_rdb_copy_dayly.sh**

```bash

#!/bin/sh

# 接收一天间隔的时间为变量，以防万一先删除以当前时间命名的文件夹，
# 再创建一个以当前时间命名的文件夹，然后拷贝rdb文件到这个文件中
cur_date=`date +%Y%m%d`
rm -rf /opt/eshop/redis/snapshotting/$cur_date
mkdir -p /opt/eshop/redis/snapshotting/$cur_date
cp /var/redis/6379/dump.rdb /opt/eshop/redis/snapshotting/$cur_date

# 接收一个一个月之前的变量，并且删除这个变量的文件
del_date=`date -d -1month +%Y%m%d`
rm -rf /opt/eshop/redis/snapshotting/$del_date
```
编写liunx的自动调度脚本

```bash
$ crontab -e
0 0 * * * sh /opt/eshop/redis/copy/redis_rdb_copy_dayly.sh
```

**测试脚本**

编写好以上的脚本后，最短的一个间隔都是下一个小时的0分钟才开始备份，所以不能立即看到脚本是否能生效，所以我们可以手动
来测试一下脚本是否能备份,就以每小时的脚本为例

```bash
# 获取一下48小时之前的时间，假如是2019040214
$ date -d -48hour +%Y%m%d%k
# 那么我们可以在snapshotting文件夹下创建一个2019040214文件夹把/var/redis/6379/dump.rdb拷贝进来
$ mkdir /opt/eshop/redis/snapshotting/2019040214
$ cp /var/redis/6379/dump.rdb /opt/eshop/redis/snapshotting/2019040214
$ sh /opt/eshop/redis/copy/redis_rdb_copy_hourly.sh
$ ls /opt/eshop/redis/snapshotting
2019040414
```
可以看到2019040214这个文件被删除了，重新备份了一份变成了2019040414，从4月2变成4月4

## 灾难场景和数据恢复方案

1.如果是redis进程挂掉，那么重启redis进程即可，直接基于AOF日志文件恢复数据

- 不演示了，在上一章的AOF数据恢复那一块
[AOF数据恢复](https://github.com/ywhs/YwhSpringCloud/blob/master/document/chapter1-20/%E5%A6%82%E4%BD%95%E5%AE%9E%E7%8E%B0redis%E7%9A%84%E6%8C%81%E4%B9%85%E5%8C%96.md#aof%E6%8C%81%E4%B9%85%E5%8C%96%E7%9A%84%E6%95%B0%E6%8D%AE%E6%81%A2%E5%A4%8D%E5%AE%9E%E9%AA%8C)，演示了，fsync everysec，最多就丢一秒的数

2.如果是redis进程所在机器挂掉，那么重启机器后，尝试重启redis进程，尝试直接基于AOF日志文件进行数据恢复

- AOF没有破损，也是可以直接基于AOF恢复的, AOF append-only，顺序写入，如果AOF文件破损，那么用redis-check-aof fix

3.如果redis当前最新的AOF和RDB文件出现了丢失/损坏，那么可以尝试基于该机器上当前的某个最新的RDB数据副本进行数据恢复

- 当前最新的AOF和RDB文件都出现了丢失/损坏到无法恢复，一般不是机器的故障，人为一般，有人不小心就把存储的大量的数据文件对应的目录，rm -rf一下
/var/redis/6379下的文件给删除了，这时我们应该找到RDB最新的一份备份，小时级的备份，copy到redis里面去，就可以恢复到某一个小时之内的数据了

**容灾演练开始**

- 人为模拟删除持久化文件，在我们的备份文件/opt/eshop/redis/snapshotting/2019040810/dump.rdb拷贝到/var/redis/6379/下

```bash
$ rm -rf /var/redis/6379/*
$ cp /opt/eshop/redis/snapshotting/2019040810/dump.rdb /var/redis/6379/
```

- 关闭redis 重新启动redis,查看数据是否恢复

```bash
$ redis-cli SHUTDOWN
$ /etc/init.d/redis_6379 start
$ redis-cli
$ get k1
$ (nil)
```

**rdb文件恢复数据遇到的问题**

- 这时数据显示为空，我明明把rdb文件拷贝到了持久化文件中了啊，为什么呢？这是因为我开启了AOF持久化，优先使用AOF文件来恢复我们的文件，
而AOF文件appendonly.aof是你插入一条把指令存储的文件，你重新启动是没有指令发生的，所以aof文件是空的，那么自然数据也是空的。

- 这时你cat rdb文件，你会发现跟我们备份的文件内容不一样，这是因为我们的redis到了检查点的时间间隔，基于内存又重新生成了一份rdb文件
覆盖了我们的rdb文件

**解决rdb文件恢复数据**

为了解决以上两种问题我们应该在关闭redis后，在配置文件中暂时把AOF持久化关闭，再把备份数据文件拷贝到持久化文件中，查看数据是否恢复

```bash
$ redis-cli SHUTDOWN
$ vim /etc/redis/6379.conf
appendonly no
$ rm -rf /var/redis/6379/*
$ cp /opt/eshop/redis/snapshotting/2019040810/dump.rdb /var/redis/6379/
$ /etc/init.d/redis_6379 start
$ redis-cli
$ get k1
"v1"
```

**注意**

可以看到我们的数据已经恢复了，但是AOF持久化还没有开启，所以还要开启redis的AOF持久化，但是这时不能关闭redis重新开启AOF持久化，因为现在的AOF
文件还是空的，如果你开启了AOF，那么数据又被清空了，优先使用了aof文件来做恢复了，那应该怎么办呢，我们应该先使用命令暂时修改AOF开启，等待aof文件
与rdb文件同步，再手动配置我们的配置文件启动即可

> config get 配置名
>
> config set 配置名

```bash
$ redis-cli
$ config get appendonly
"appendonly"
"no"
$ config set appendonly yes
OK
$ config get appendonly
"appendonly"
"yes"
# 等待一会，看我们的持久化文件下生成了appendonly.aof文件后，即可重新启动redsi
$ exit
$ redis-cli SHUTDOWN
$ vim /etc/redis/6379.conf
appendonly yes
$ /etc/init.d/redis_6379 start
$ redis-cli
$ get k1
"v1"
```
此时我们的aof文件和rdb文件全部都是恢复过来了。

4.如果当前机器上的所有RDB文件全部损坏，那么从远程的云服务上拉取最新的RDB快照回来恢复数据

5.如果是发现有重大的数据错误，比如某个小时上线的程序一下子将数据全部污染了，数据全错了，那么可以选择某个更早的时间点，对数据进行恢复

- 举个例子，12点上线了代码，发现代码有bug，导致代码生成的所有的缓存数据，写入redis，全部错了
找到一份11点的rdb的冷备，然后按照上面的步骤，去恢复到11点的数据，不就可以了吗







