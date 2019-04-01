# SpringCloud第五章学习笔记

## 注意

spring-cloud-config的配置一定要放到bootstrap.properties文件中，当然有的属性可以放到其他配置文件中，但是为了不出错，所以规定自己一定要把所有关于config的配置信息都放到bootstrap.properties中
```bash
1、configclient 服务启动后，默认会先访问bootstrap.yml，然后绑定configserver，然后获取application.yml 配置。
如果仅仅在application.yml 配置了url:http://127.0.0.1:8080 这样默认会使用8888端口，（配置无效）。
2、所以我们将绑定configserver的配置属性应该放在bootstrap.yml文件里。
```

### bootstrap/ application 的应用场景

application 配置文件这个容易理解，主要用于 Spring Boot 项目的自动化配置。

bootstrap 配置文件有以下几个应用场景：

- 使用 Spring Cloud Config 配置中心时，这时需要在 bootstrap 配置文件中添加连接到配置中心的配置属性来加载外部配置中心的配置信息；

- 些固定的不能被覆盖的属性

- 一些加密/解密的场景


## 简介


在分布式系统中，由于服务数量巨多，为了方便服务配置文件统一管理，实时更新，所以需要分布式配置中心组件。在Spring Cloud中，有分布式配置中心组件spring cloud config ，它支持配置服务放在配置服务的内存中（即本地），
也支持放在远程Git仓库中。在spring cloud config 组件中，分两个角色，一是config server，二是config client。

这里的客户端是指我们具体的业务的服务或者其他的组件，并不是一定要有这个客户端才可以。

我们使用git的仓库服务，那么我们就需要一个配置仓库了，以下的仓库是一个公开的，是方志朋老师创建的，可以放心大胆的使用，之后我也会自己创建一个我自己的配置仓库的。

## 构建config-server

创建一个spring-boot项目，取名为config-server，引入spring-cloud-config-server依赖

在程序的入口Application类加上@EnableConfigServer注解开启配置服务器的功能

需要在程序的配置文件application.properties文件配置以下：

```yml
spring.application.name=config-server
server.port=8888
# 配置git仓库地址 如果Git仓库为公开仓库，可以不填写用户名和密码，如果是私有仓库需要填写，本例子是公开仓库，放心使用
spring.cloud.config.server.git.uri=https://github.com/forezp/SpringcloudConfig/
# 配置仓库路径
spring.cloud.config.server.git.search-paths=respo
# 配置仓库的分支
spring.cloud.config.label=master
# 访问git仓库的用户名
spring.cloud.config.server.git.username=
# 访问git仓库的用户密码
spring.cloud.config.server.git.password=
```
如果Git仓库为公开仓库，可以不填写用户名和密码，如果是私有仓库需要填写，本例子是公开仓库，放心使用。

http请求地址和资源文件映射如下:

- /{application}/{profile}/{label}
- /{application}-{profile}.yml
- /{label}/{application}-{profile}.yml
- /{application}-{profile}.properties
- /{label}/{application}-{profile}.properties

配置文件的**profile**可以有个以下几个

- prod  生产环境

- dev 开发环境

- test 测试环境

## 构建一个config client

重新创建一个springboot项目，取名为config-client,引入spring-cloud-starter-config依赖，记得跟上面的server不一样

其配置文件bootstrap.properties：
```yml
# 注意这个名称要与git上的配置文件名称profile前边的名字一样，否则找不到相应的配置文件
spring.application.name=config-client
# 指明远程仓库的分支
spring.cloud.config.label=master
# dev开发环境配置文件     test测试环境      pro正式环境
spring.cloud.config.profile=dev
# 指明配置服务中心的网址。
spring.cloud.config.uri=http://localhost:8888
# 是从配置中心读取文件。

# 端口号
server.port=8881
# eureka配置注册到服务中心
eureka.client.serviceUrl.url=http://localhost:8761/eureka/

```
程序的入口类，写一个API接口“／hi”，返回从配置中心读取的foo变量的值，代码如下：

```java

@SpringBootApplication
@RestController
public class ConfigClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConfigClientApplication.class, args);
	}


	@Value("${foo}")
	String foo;

	@RequestMapping(value = "/hi")
	public String hi(){
		return foo;
	}

}

```

ok,以上配置完毕以后，我们就可以访问远程的git仓库并读取配置，以上客户端连接config服务是通过ip:port的形式来访问的，并不是把两个都注册到Eureka服务中心。

所以现在的架构图是以下形式：
![config架构图](https://user-images.githubusercontent.com/34649300/55221728-1f212e00-5245-11e9-8cf1-8d56a51aa1fb.png)

非常的单一，因为我们上面是通过ip加端口形式寻找config-server的，如果这一个配置中心宕机了，那么所有微服务的配置都将得不到更新，所以我们可以通过把配置中心也注册到Eureka的服务中心
这样我们就可以通过应用名称来寻找了，多启动几个配置中心（分别部署到不同的机器上），这样如果其中一个宕机，还有另一个为我们提供配置，修改为下面的架构：

![修改后的架构图](https://user-images.githubusercontent.com/34649300/55222020-d1f18c00-5245-11e9-97ca-269581ead03f.png)


## 修改项目注册到Eureka服务中心中

我们需要一个Eureka服务中心的项目模块，可以使用上一章节的服务中心，或者在这个章节中重新创建一个服务中心模块并启动。

**修改Eureka-server模块**

- 在其pom.xml文件加上EurekaClient的起步依赖spring-cloud-starter-netflix-eureka-client

- 配置文件application.properties，指定服务注册地址为http://localhost:8761/eureka/
```yml
eureka.client.serviceUrl.defaultZone=http://localhost:8761/eureka/
```

**修改config-client**

- 在其pom.xml文件加上EurekaClient的起步依赖spring-cloud-starter-netflix-eureka-client

- 配置文件application.properties,添加以下内容：

```yml
eureka.client.serviceUrl.defaultZone=http://localhost:8761/eureka/
# 是从配置中心读取文件
spring.cloud.config.discovery.enabled=true
# 配置中心的servieId，即服务名。如果使用服务名来读取配置文件就不许要配置服务中心的网址了
spring.cloud.config.discovery.serviceId=config-server
```

- 注释掉以下内容,使用在服务注册中心查找config服务了，这样就可以启动多个config服务中心，不怕宕机了。

```yml
#spring.cloud.config.uri= http://localhost:8888/
```
这时发现，在读取配置文件不再写ip地址，而是服务名，这时如果配置服务部署多份，通过负载均衡，从而高可用。
为什么不通过ip地址来连接config-server呢，是因为如果通过ip地址来访问的话，那么config-server改变地址时，这里的配置就需要改变了，而注册到eureka中，则不需要知道ip地址，只需要知道服务名称即可，其中一个服务挂掉，还可以找另一个。

## 测试发现

按理说这个时候如果启动多个config-server多个实例，如果部署到不同的机器上就称之为集群，宕机一个还是会得到git上的数据的这个没错，但是我尝试把所有的config-server全部宕机还是会得到数据

- 分析原因
有可能在启动的时候把git上的配置文件全部down到了本地作为缓存，所以这时所有的config-server全部宕机还是有可能得到配置数据的
但是在我更新数据时，尝试调用http://localhost:8882/actuator/refresh(这个是在没有加rabbitMQ的刷新地址)，控制台报错，连接config-server超时无法更新，但是还是能访问旧的数据的。

## 遗留问题

我们可以在git上放所有微服务的配置文件，当我们更新时需要微服务感知到，但是现在微服务是感知不到的，所以我们要告诉微服务我们更新配置，你要使用最新的配置啊

还有就是当一个公共的配置文件被多个微服务使用，我们更新后要通知所有的微服务才可以，

下一章实现这两个问题

- 通知微服务更新（使用actuator监控我们的配置文件，通过post请求刷新配置）

- 通知所有微服务更新（rabbitMq消息总线，转告我们所有的微服务更新）


## 参考：

- [史上最简单的SpringCloud教程 | 第六篇: 分布式配置中心(Spring Cloud Config)(Finchley版本)](https://blog.csdn.net/forezp/article/details/81041028)

- [史上最简单的SpringCloud教程 | 第七篇: 高可用的分布式配置中心(Spring Cloud Config)(Finchley版本)](https://blog.csdn.net/forezp/article/details/81041045)
