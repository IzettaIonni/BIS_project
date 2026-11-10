package kz.gymbooking.controller;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import kz.gymbooking.dto.ConnectDeviceRequest;
import kz.gymbooking.dto.MetricType;
import kz.gymbooking.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "text/plain;charset=UTF-8")
public class DeviceController {

    @GetMapping("/device-providers")
    public String providers() {
        return """
                stub, Stub device (fake data), enabled
                garmin, Garmin Connect, disabled
                fitbit, Fitbit, disabled
                """;
    }

    @GetMapping("/users/me/devices")
    public String myDevices() {
        return "#11 stub, My band, CONNECTED, last sync 2026-11-10T09:00:00Z\n";
    }

    @PostMapping("/users/me/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public String connect(@Valid @RequestBody ConnectDeviceRequest req) {
        if (!req.provider().equals("stub")) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Only 'stub' provider is enabled");
        }
        return "id: 12\nprovider: stub\nname: " + req.name() + "\nstatus: CONNECTED\n";
    }

    @DeleteMapping("/users/me/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable int deviceId) {
    }

    @PostMapping("/users/me/devices/{deviceId}/sync")
    public String sync(@PathVariable int deviceId) {
        return "imported: 48\nlastSyncAt: " + OffsetDateTime.now() + "\n";
    }

    @GetMapping("/users/me/metrics")
    public String metrics(@RequestParam(required = false) MetricType type,
                          @RequestParam(required = false) OffsetDateTime from,
                          @RequestParam(required = false) OffsetDateTime to,
                          @RequestParam(required = false) Integer bookingId) {
        return """
                HEART_RATE 132 bpm, 2026-11-05T07:30:00Z
                CALORIES 410 kcal, 2026-11-05T08:00:00Z
                STEPS 8450 steps, 2026-11-09T20:00:00Z
                """;
    }
}
