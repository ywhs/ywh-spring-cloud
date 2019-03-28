package com.ywh.servicezuul.filter;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.exception.ZuulException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * CreateTime: 2019-03-28 16:43
 * ClassName: MyFilter
 * Package: com.ywh.servicezuul.filter
 * Describe:
 * zuul的过滤器
 *
 * @author YWH
 */
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
