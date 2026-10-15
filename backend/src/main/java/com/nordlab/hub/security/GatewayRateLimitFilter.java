package com.nordlab.hub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+20)
public class GatewayRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MS=60_000;
    private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
    private final int generalLimit,authLimit;
    private final Clock clock=Clock.systemUTC();
    private final AtomicInteger calls=new AtomicInteger();
    public GatewayRateLimitFilter(@Value("${nordhub.gateway.requests-per-minute:120}")int generalLimit,@Value("${nordhub.gateway.auth-requests-per-minute:10}")int authLimit){this.generalLimit=generalLimit;this.authLimit=authLimit;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        if(!request.getRequestURI().startsWith("/api/")){chain.doFilter(request,response);return;}
        long now=clock.millis();boolean auth=request.getRequestURI().matches("/api/v1/auth/(login|register)");
        int limit=auth?authLimit:generalLimit;String key=request.getRemoteAddr()+":"+(auth?"auth":"api");
        Window window=windows.compute(key,(k,current)->{if(current==null||now-current.start>=WINDOW_MS)return new Window(now,new AtomicInteger(1));current.count.incrementAndGet();return current;});
        if(calls.incrementAndGet()%500==0)windows.entrySet().removeIf(e->now-e.getValue().start>=WINDOW_MS*2);
        if(window.count.get()>limit){response.setStatus(429);response.setHeader("Retry-After","60");response.setContentType(MediaType.APPLICATION_JSON_VALUE);response.getWriter().write("{\"error\":\"rate_limited\",\"message\":\"Too many requests. Try again shortly.\"}");return;}
        response.setHeader("X-RateLimit-Limit",String.valueOf(limit));response.setHeader("X-RateLimit-Remaining",String.valueOf(Math.max(0,limit-window.count.get())));chain.doFilter(request,response);
    }
    private record Window(long start,AtomicInteger count){}
}
