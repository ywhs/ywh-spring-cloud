# springCloud第四章笔记  路由网关(zuul)

## 简介

在微服务架构中，需要几个基础的服务治理组件，包括服务注册与发现、服务消费、负载均衡、断路器、智能路由、配置管理等，由这几个基础组件相互协作，共同组建了一个简单的微服务系统。一个简答的微服务系统如下图：

![架构图](https://user-images.githubusercontent.com/34649300/55141786-1f062d00-5176-11e9-84a2-a94db16bc56e.png)

A服务和B服务是可以相互调用的，作图的时候忘记了。并且配置服务也是注册到服务注册中心的

在Spring Cloud微服务系统中，一种常见的负载均衡方式是，客户端的请求首先经过负载均衡（zuul、Ngnix），再到达服务网关（zuul集群），然后再到具体的服。
服务统一注册到高可用的服务注册中心集群，服务的所有的配置文件由配置服务管理（下一篇文章讲述），配置服务的配置文件放在git仓库，方便开发人员随时改配置。

Zuul的主要功能是路由转发和过滤器。路由功能是微服务的一部分，比如／api/user转发到到user服务，/api/shop转发到到shop服务。zuul默认和Ribbon结合实现了负载均衡的功能。

## 使用网关的好处

- 后端暴露接口往往都是动态调整的，使用网关代理转发，简化前端调用的难度，可以不用考虑每个微服务的host，port以及负载和动态调整，只要知道调用的是哪个微服务即可。

- 使用网关后可以不直接暴露接口给外部，强化了后端微服务的安全性。并且可以进行token验证。

- 网关可以进行服务聚合，将一些通用请求聚合在一起，减少请求次数。

- 可以配合shiro进行一系列权限校验工作。

## 开始

依次启动eureka-server、eureka-hi等工程

- 继续使用上一节的工程。在原有的工程上，创建一个新的工程模块。名字为service-zuul工程，pom文件中引入spring-cloud-starter-netflix-zuul依赖。

- 在其入口applicaton类加上注解@EnableZuulProxy，开启zuul的功能

- 配置文件application.yml端口，应用名称，eureka的注册中心等，最主要的是在使用zuul后，ribbon的熔断机制失效了，必须配置两个属性又生效了
```yml
# 以下属性如果注释掉，那么ribbon的熔断机制则会失效，至于为什么加上这两属性就好了，没搞懂
ribbon:
  ReadTimeout: 60000
  ConnectTimeout: 60000
```

首先指定服务注册中心的地址为http://localhost:8761/eureka/，服务的端口为8769，服务名为service-zuul；以/api-a/ 开头的请求都转发给service-ribbon服务；以/api-b/开头的请求都转发给service-feign服务；

打开浏览器访问：这说明zuul起到了路由的作用

http://localhost:8769/api-a/hi?name=ywh ;浏览器显示：
> hi ywh,i am from port:8762

http://localhost:8769/api-b/hi?name=ywh ;浏览器显示：
> hi ywh,i am from port:8762

## 服务过滤

zuul不仅只是路由，并且还能过滤，做一些安全验证。继续改造工程；

```java
@Component
public class MyFilter extends ZuulFilter {

    private static final Logger LOG = LoggerFactory.getLogger(MyFilter.class);

    /**
     *  filterType：返回一个字符串代表过滤器的类型，在zuul中定义了四种不同生命周期的过滤器类型，具体如下：
         pre：路由之前
         routing：路由之时
         post： 路由之后
         error：发送错误调用
     * @return 返回其中一个状态
     */
    @Override
    public String filterType() {
        return "pre";
    }

    /**
     * 过滤的顺序
     * @return 过滤的顺序
     */
    @Override
    public int filterOrder() {
        return 0;
    }

    /**
     * 这里可以写逻辑判断，是否要过滤，本文true,永远过滤。
     * @return  是否过滤
     */
    @Override
    public boolean shouldFilter() {
        return true;
    }

    /**
     * 过滤器的具体逻辑。可用很复杂，包括查sql，nosql去判断该请求到底有没有权限访问。
     * 以下代码暂时是对请求中是否有token属性
     * @return 任意对象
     * @throws ZuulException 异常信息
     */
    @Override
    public Object run() throws ZuulException {
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();
        LOG.info(String.format("%s >>> %s", request.getMethod(), request.getRequestURL().toString()));
        Object accessToken = request.getParameter("token");
        if(accessToken == null) {
            LOG.warn("token is empty");
            ctx.setSendZuulResponse(false);
            ctx.setResponseStatusCode(401);
            try {
                ctx.getResponse().getWriter().write("token is empty");
            }catch (Exception e){
                LOG.error("zuul报错信息 => " ,e);
            }

            return null;
        }
        LOG.info("token => " + accessToken);
        return null;
    }
}
```

这时候访问：http://localhost:8769/api-a/hi?name=ywh ；网页显示:
> token is empty

访问 http://localhost:8769/api-a/hi?name=ywh&token=22
> hi ywh , I am from port:8762


参考：
- [史上最简单的SpringCloud教程 | 第五篇: 路由网关(zuul)(Finchley版本)](https://blog.csdn.net/forezp/article/details/81041012)


