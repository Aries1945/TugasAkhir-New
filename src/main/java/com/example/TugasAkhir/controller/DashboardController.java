package com.example.TugasAkhir.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;


 @Controller 
public class DashboardController {
    

    @GetMapping("/")
    public String Dashboard(){
        return "redirect:/dashboard.html";
    }

}
