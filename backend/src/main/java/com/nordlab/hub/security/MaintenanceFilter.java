package com.nordlab.hub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nordlab.hub.service.MaintenanceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+30)
public class MaintenanceFilter extends OncePerRequestFilter {
    private final MaintenanceService maintenance;private final ObjectMapper mapper;
    public MaintenanceFilter(MaintenanceService maintenance,ObjectMapper mapper){this.maintenance=maintenance;this.mapper=mapper;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        String path=request.getRequestURI(),mode=maintenance.mode();boolean api=path.startsWith("/api/v1/");boolean write=!java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod());
        boolean control=path.equals("/api/v1/config")||path.startsWith("/api/v1/setup/")||path.equals("/api/v1/gateway/maintenance")||path.equals("/api/v1/gateway/status")||path.startsWith("/api/v1/auth/")||path.equals("/api/v1/health");
        boolean blocked=api&&!control&&(("READ_ONLY".equals(mode)&&write)||"FULL".equals(mode));
        if(blocked){response.setStatus(503);response.setHeader("Retry-After","300");response.setContentType(MediaType.APPLICATION_JSON_VALUE);mapper.writeValue(response.getOutputStream(),Map.of("error","maintenance","mode",mode,"message",maintenance.message()==null?"The service is temporarily unavailable":maintenance.message()));return;}
        chain.doFilter(request,response);
    }
}
