package com.nordlab.hub.api;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/config")
public class ApiConfigController {
    @GetMapping public Map<String,Object> config(){return Map.of("apiEnabled",true,"apiVersion","v1","setupWizard",true);}
}
