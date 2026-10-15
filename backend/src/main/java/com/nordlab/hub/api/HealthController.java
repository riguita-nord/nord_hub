package com.nordlab.hub.api;

import java.sql.Connection;
import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final DataSource dataSource;
    public HealthController(DataSource dataSource){this.dataSource=dataSource;}
    @GetMapping public Map<String,Object> health(){boolean db;try(Connection c=dataSource.getConnection()){db=c.isValid(2);}catch(Exception e){db=false;}return Map.of("status",db?"UP":"DEGRADED","database",db?"UP":"DOWN","checkedAt",Instant.now().toString());}
}
