# springCloud第二章笔记

在上一篇文章，讲了服务的注册和发现。在微服务架构中，业务都会被拆分成一个独立的服务，服务与服务的通讯是基于http restful的。
Spring cloud有两种服务调用方式，一种是ribbon+restTemplate，另一种是feign。在这一篇文章首先讲解下基于ribbon+rest

## 采用Ribbon调用微服务
### Ribbon简介
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

### 准备工作

需要启动两个eureka-hi服务，端口号分别是8762、8763，如何在Idea中启动两个同一个项目参考以下

- [如何在IDEA启动多个Spring Boot工程实例（同一个项目）](https://blog.csdn.net/qq_36956154/article/details/80183221)

按照顺序启动EurekaServerApplication、EurekaHiApplication:8762、EurekaHiApplication:8763

访问localhost:8761可以查看Eureka服务注册中心启动两个客户端。


### 创建消费者

重新新建一个spring-boot的工程模块，取名为：service-ribbon;
- 修改pom文件
- 配置application.yml文件
- 创建一个service，调用restTemplate.getForObject("http://EUREKA-HI/hi?name="+name,String.class);
> EUREKA-HI是我们在客户端项目中定义的项目名称（代表了域名会解析成相应的ip加端口号），也是eureka注册中心Application的名称。
- 创建controller调用service

在浏览器上多次访问http://localhost:8764/hi?name=forezp，浏览器交替显示：

> hi forezp,i am from port:8762

> hi forezp,i am from port:8763

这说明当我们通过调用restTemplate.getForObject(“http://EUREKA-HI/hi?name=”+name,String.class)方法时，已经做了负载均衡，访问了不同的端口的服务实例。

关于什么是负载均衡，我的理解为，有两台机器做同一件事情，当有一台机器不工作时，也不会导致全线崩溃。

### 此时的架构

![在这里插入图片描述](https://img-blog.csdnimg.cn/20190326183911389.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3FxXzM2OTU2MTU0,size_16,color_FFFFFF,t_70)

- 一个服务注册中心，eureka server,端口为8761
- service-hi工程跑了两个实例，端口分别为8762,8763，分别向服务注册中心注册
- sercvice-ribbon端口为8764,向服务注册中心注册
- 当sercvice-ribbon通过restTemplate调用service-hi的hi接口时，因为用ribbon进行了负载均衡，会轮流的调用service-hi：8762和8763 两个端口的hi接口；

## 采用Feign调用微服务

### 一、Feign简介

Feign是一个声明式的伪Http客户端，它使得写Http客户端变得更简单。使用Feign，只需要创建一个接口并注解。
它具有可插拔的注解特性，可使用Feign 注解和JAX-RS注解。Feign支持可插拔的编码器和解码器。Feign默认集成了Ribbon，并和Eureka结合，默认实现了负载均衡的效果。

简而言之：

- Feign 采用的是基于接口的注解
- Feign 整合了ribbon，具有负载均衡的能力
- 整合了Hystrix，具有熔断的能力

### 二、准备工作
启动eureka-server，端口为8761; 启动service-hi 两次，端口分别为8762 、8773.

### 三、创建一个feign的服务
- 新建一个spring-boot工程模块，取名为serice-feign，在它的pom文件引入Feign的起步依赖spring-cloud-starter-feign、Eureka的起步依赖spring-cloud-starter-netflix-eureka-client、Web的起步依赖spring-boot-starter-web

- 在工程的配置文件application.yml文件，指定程序名为service-feign，端口号为8765，服务注册地址为http://localhost:8761/eureka/ 

- 在程序的启动类ServiceFeignApplication ，加上@EnableFeignClients注解开启Feign的功能：

- 定义一个feign接口，通过@ FeignClient（“服务名”），来指定调用哪个服务。比如在代码中调用了service-hi服务的“/hi”接口
> 这个接口不需要具体的实现，具体的实现相当于要调用的服务名的controller中相应的方法

- Web层的controller层，对外暴露一个"/hi"的API接口，通过上面定义的Feign客户端SchedualServiceHi 来消费服务

- 启动程序，多次访问http://localhost:8765/hi?name=ywh,浏览器交替显示：

> hi ywh,i am from port:8762
> hi ywh,i am from port:8763


参考：
- [史上最简单的SpringCloud教程 | 第二篇: 服务消费者（rest+ribbon）(Finchley版本)](https://blog.csdn.net/forezp/article/details/81040946)

- [什么时负载均衡](https://zhuanlan.zhihu.com/p/32841479)

