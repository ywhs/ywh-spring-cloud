# springCloud第二章笔记

在上一篇文章，讲了服务的注册和发现。在微服务架构中，业务都会被拆分成一个独立的服务，服务与服务的通讯是基于http restful的。Spring cloud有两种服务调用方式，一种是ribbon+restTemplate，另一种是feign。在这一篇文章首先讲解下基于ribbon+rest

## Ribbon简介
Ribbon实现客户端的负载均衡，负载均衡器提供很多对http和tcp的行为控制。Spring cloud Feign已经集成Ribbon，所以注解@FeignClient的类，默认实现了ribbon的功能。

Ribbon主要包括如下功能

- 1.支持通过DNS和IP和服务端通信
- 2.可以根据算法从多个服务中选取一个服务进行访问
- 3.通过将客户端和服务器分成几个区域（zone）来建立客户端和服务器之间的关系。客户端尽量访问和自己在相同区域(zone)的服务，减少服务的延迟
- 4.保留服务器的统计信息，ribbon可以实现用于避免高延迟或频繁访问故障的服务器
- 5.保留区域(zone)的统计数据，ribbon可以实现避免可能访问失效的区域(zone)

Ribbon主要包含如下组件：

- 1.IRule
- 2.IPing
- 3.ServerList
- 4.ServerListFilter
- 5.ServerListUpdater
- 6.IClientConfig
- 7.ILoadBalancer

## 准备工作

需要启动两个eureka-hi服务，端口号分别是8762、8763，如何在Idea中启动两个同一个项目参考以下

- [如何在IDEA启动多个Spring Boot工程实例（同一个项目）](https://blog.csdn.net/qq_36956154/article/details/80183221)

按照顺序启动EurekaServerApplication、EurekaHiApplication:8762、EurekaHiApplication:8763

访问localhost:8761可以查看Eureka服务注册中心启动两个客户端。


## 创建消费者

重新新建一个spring-boot的工程模块，取名为：service-ribbon;
- 修改pom文件
- 配置application.yml文件
- 创建一个service，调用restTemplate.getForObject("http://EUREKA-HI/hi?name="+name,String.class);
- 创建controller调用service

在浏览器上多次访问http://localhost:8764/hi?name=forezp，浏览器交替显示：

> hi forezp,i am from port:8762

> hi forezp,i am from port:8763

这说明当我们通过调用restTemplate.getForObject(“http://EUREKA-HI/hi?name=”+name,String.class)方法时，已经做了负载均衡，访问了不同的端口的服务实例。

关于什么是负载均衡，我的理解为，有两台机器做同一件事情，当有一台机器不工作时，也不会导致全线崩溃。

## 此时的架构

![在这里插入图片描述](https://img-blog.csdnimg.cn/20190326183911389.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3FxXzM2OTU2MTU0,size_16,color_FFFFFF,t_70)

- 一个服务注册中心，eureka server,端口为8761
- service-hi工程跑了两个实例，端口分别为8762,8763，分别向服务注册中心注册
- sercvice-ribbon端口为8764,向服务注册中心注册
- 当sercvice-ribbon通过restTemplate调用service-hi的hi接口时，因为用ribbon进行了负载均衡，会轮流的调用service-hi：8762和8763 两个端口的hi接口；


参考：
- [史上最简单的SpringCloud教程 | 第二篇: 服务消费者（rest+ribbon）(Finchley版本)](https://blog.csdn.net/forezp/article/details/81040946)

- [什么时负载均衡](https://zhuanlan.zhihu.com/p/32841479)

