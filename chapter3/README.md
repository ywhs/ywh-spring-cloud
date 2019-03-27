# springCloud第三章笔记  断路器（Hystrix）

## 断路器简介

在微服务架构中，根据业务来拆分成一个个的服务，服务与服务之间可以相互调用（RPC），在Spring Cloud可以用RestTemplate+Ribbon和Feign来调用。为了保证其高可用，单个服务通常会集群部署。
由于网络原因或者自身的原因，服务并不能保证100%可用，如果单个服务出现问题，调用这个服务就会出现线程阻塞，此时若有大量的请求涌入，Servlet容器的线程资源会被消耗完毕，导致服务瘫痪。
服务与服务之间的依赖性，故障会传播，会对整个微服务系统造成灾难性的严重后果，这就是服务故障的“雪崩”效应。

为了解决这个问题，业界提出了断路器模型。

什么是断路器（熔断），家里都有总闸对吧，当有一条电路电压过大，好比如一个插线板上插满了大功率的电器，这时候就有可能出现爆炸的情况，为了避免这种情况，当电压达到一定阈值时，总闸就跳闸断电，从而避免了爆炸的情况
，这就是断路器的功能。

Netflix开源了Hystrix组件，实现了断路器模式，SpringCloud对这一组件进行了整合。 在微服务架构中，一个请求需要调用多个服务是非常常见的，如下图：

![一个请求有可能调用多个服务](https://img-blog.csdnimg.cn/20190327110532748.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3FxXzM2OTU2MTU0,size_16,color_FFFFFF,t_70)

较底层的服务如果出现故障，会导致连锁故障。当对特定的服务的调用的不可用达到一个阀值（Hystric 是5秒20次） 断路器将会被打开。

![一个底层服务崩溃](https://img-blog.csdnimg.cn/20190327110634189.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3FxXzM2OTU2MTU0,size_16,color_FFFFFF,t_70)

这个时候就需要我们的断路器开始工作了，断路打开后，可用避免连锁故障，fallback方法可以直接返回一个固定值。

## 采用Ribbon实现熔断（过载保护）
### 准备工作

- 基于上一章节的工程，首先启动工程，启动eureka-server工程；启动eureka-hi工程，它的端口为8762。

- 改造eureka-ribbon 工程的代码，首先在pox.xml文件中加入spring-cloud-starter-netflix-hystrix的起步依赖：

- 在程序的启动类ServiceRibbonApplication 加@EnableHystrix注解开启Hystrix

- 改造HelloService类，在hiService方法上加上@HystrixCommand注解。该注解对该方法创建了熔断器的功能，并指定了fallbackMethod熔断方法，熔断方法直接返回了一个字符串，字符串为"hi,"+name+",sorry,error!"，代码如下：

- 启动：service-ribbon 工程，当我们访问http://localhost:8764/hi?name=ywh,浏览器显示：
> hi ywh,i am from port:8762

- 此时关闭 service-hi 工程，当我们再访问http://localhost:8764/hi?name=ywh，浏览器会显示
> hi ,ywh, orry, error

这就说明当 service-hi 工程不可用的时候，service-ribbon调用 service-hi的API接口时，会执行快速失败，直接返回一组字符串，而不是等待响应超时，这很好的控制了容器的线程阻塞。

## 采用Feign实现熔断（过载保护）

Feign是自带断路器的，在D版本的Spring Cloud之后，它没有默认打开。需要在配置文件中配置打开它
> feign.hystrix.enabled=true

- 基于service-feign工程进行改造，只需要在FeignClient的SchedualServiceHi接口的注解中加上fallback属性指定类就行了

- SchedualServiceHiHystric需要实现SchedualServiceHi 接口，并注入到Ioc容器中
> 这个实现可以作为错误的处理,好比如前端访问后端时,返回一个500状态码,前端需要做处理一样
参考：
- [史上最简单的SpringCloud教程 | 第四篇:断路器（Hystrix）(Finchley版本)](https://blog.csdn.net/forezp/article/details/81040990)


