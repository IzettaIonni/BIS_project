package kz.gymbooking.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping(value = "/", produces = "text/plain;charset=UTF-8")
    public String home() {
        return "Gym Booking API stub is running\n";
    }
}
