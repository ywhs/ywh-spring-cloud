# springCloud注册与发现

spring cloud 为开发人员提供了快速构建分布式系统的一些工具，包括配置管理、服务发现、断路器、路由、微代理、事件总线、全局锁、决策竞选、
分布式会话等等。它运行环境简单，可以在开发人员的电脑上跑。另外说明spring cloud是基于springboot的，所以需要开发中对springboot有一定的了解，
另外对于“微服务架构” 不了解的话，可以通过搜索引擎搜索“微服务架构”了解下。

- [图解什么是微服务](https://www.itcodemonkey.com/article/1914.html)


目前版本为Spring Boot版本2.1.3.RELEASE,Spring Cloud版本为Greenwich.RELEASE。

## 首先创建一个maven主工程

在创建主项目时下面图片中选择框起来的选项

![20190403165610](https://user-images.githubusercontent.com/34649300/55466311-8702ba80-5631-11e9-940b-d6858b24f074.jpg)

pom文件中引入依赖，[父pom文件代码地址](https://github.com/ywhs/YwhSpringCloud/blob/master/chapter1/pom.xml)，这个pom文件作为父pom文件，起到依赖版本控制的作用，其他module工程继承该pom

然后创建2个model工程:**一个model工程作为服务注册中心，即Eureka Server,另一个作为Eureka Client。

右键工程->创建model-> 选择spring initialir

## 创建eureka server模块

- 创建完后的工程，其pom.xml继承了父pom文件，并引入spring-cloud-starter-netflix-eureka-server的依赖
[eureka server的pom文件代码地址](https://github.com/ywhs/YwhSpringCloud/blob/master/chapter1/eureka-server/pom.xml)

- 启动一个服务注册中心，只需要一个注解@EnableEurekaServer，这个注解需要在springboot工程的启动application类上加

```java

@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run( EurekaServerApplication.class, args );
    }
}
```

- eureka是一个高可用的组件，它没有后端缓存，每一个实例注册之后需要向注册中心发送心跳（因此可以在内存中完成），在默认情况下erureka server
也是一个eureka client ,必须要指定一个 server。eureka server的配置文件appication.yml：

```yml
# 端口号
server:
  port: 8761

# eureka配置，配置注册服务中心
eureka:
  instance:
    hostname: localhost
  client:
    # 表明自己不是一个客户端，并且不进行自注册
    register-with-eureka: false
    fetch-registry: false
    service-url:
      url: http://${eureka.instance.hostname}:${server.port}/eureka/
# 配置应用程序名称
spring:
  application:
    name: eureka-server
```
通过eureka.client.registerWithEureka：false和fetchRegistry：false来表明自己是一个eureka server.

- eureka server 是有界面的，启动工程,打开浏览器访问：http://localhost:8761 ,界面如下：

![Eureka注册界面](https://user-images.githubusercontent.com/34649300/55466947-d990a680-5632-11e9-916c-46910182d5da.png)

>No application available 没有服务被发现 ……_
 因为没有注册服务当然不可能有服务被发现了。

## 创建eureka hi模块**
当client向server注册时，它会提供一些元数据，例如主机和端口，URL，主页等。Eureka server 从每个client实例接收心跳消息。 如果心跳超时，
则通常将该实例从注册server中删除。

创建过程同server类似,[pom.xml地址](https://github.com/ywhs/YwhSpringCloud/blob/master/chapter1/eureka-hi/pom.xml)

- 通过注解@EnableEurekaClient 表明自己是一个eurekaclient，实际上在Edgware版本之后，不再需要添加该注解，不过写上也没有关系。

```java

@SpringBootApplication
@EnableEurekaClient
@RestController
public class EurekaHiApplication {

    public static void main(String[] args) {
        SpringApplication.run( ServiceHiApplication.class, args );
    }

    @Value("${server.port}")
    String port;

    @RequestMapping("/hi")
    public String home(@RequestParam(value = "name", defaultValue = "forezp") String name) {
        return "hi " + name + " ,i am from port:" + port;
    }

}

```

- 还需要在配置文件中注明自己的服务注册中心的地址，application.yml配置文件如下

```yml
# 端口号
server:
  port: 8762
# 配置应用程序名称
spring:
  application:
    name: eureka-hi

# eureka配置 客户端配置，注册到服务中心
eureka:
  client:
    service-url:
      url: http://localhost:8761/eureka/
```
需要指明spring.application.name,这个很重要，这在以后的服务与服务之间相互调用一般都是根据这个name 。
启动工程，打开http://localhost:8761 ，即eureka server 的网址：

![20190403181446](https://user-images.githubusercontent.com/34649300/55471516-7e63b180-563c-11e9-845a-deffd8f296ac.png)

发现一个服务已经注册在服务中了 ,端口为8762,服务名是EUREKA-HI
打开 http://localhost:8762/hi?name=ywh ，你会在浏览器上看到 :

> hi ywh,i am from port:8762



