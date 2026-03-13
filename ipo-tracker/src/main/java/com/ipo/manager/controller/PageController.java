package com.ipo.manager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/ipo")
    public String ipo() {
        return "ipo";
    }

    @GetMapping("/broker-fees")
    public String brokerFees() {
        return "broker-fees";
    }

    @GetMapping("/calendar")
    public String calendar() {
        return "calendar";
    }
}
