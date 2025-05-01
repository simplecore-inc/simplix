package dev.simplecore.simplix.demo.web.event.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping("/event/message-demo")
    public String index() {
        return "/event/message-demo";
    }
    
    @GetMapping("/event/system-monitor")
    public String systemMonitor() {
        return "/event/system-monitor";
    }
} 