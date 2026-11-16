package kz.gymbooking.controller;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.validation.Valid;
import kz.gymbooking.domain.MetricType;
import kz.gymbooking.dto.ConnectDeviceRequest;
import kz.gymbooking.dto.DeviceProviderResponse;
import kz.gymbooking.dto.DeviceResponse;
import kz.gymbooking.dto.MetricResponse;
import kz.gymbooking.dto.SyncResultResponse;
import kz.gymbooking.security.AuthUser;
import kz.gymbooking.service.DeviceService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeviceController {

    private final DeviceService devices;

    public DeviceController(DeviceService devices) {
        this.devices = devices;
    }

    @GetMapping("/device-providers")
    public List<DeviceProviderResponse> providers() {
        return devices.providers();
    }

    @GetMapping("/users/me/devices")
    public List<DeviceResponse> myDevices(@AuthenticationPrincipal Jwt jwt) {
        return devices.listMine(AuthUser.from(jwt));
    }

    @PostMapping("/users/me/devices")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceResponse connect(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConnectDeviceRequest req) {
        return devices.connect(AuthUser.from(jwt), req);
    }

    @DeleteMapping("/users/me/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal Jwt jwt, @PathVariable Long deviceId) {
        devices.disconnect(AuthUser.from(jwt), deviceId);
    }

    @PostMapping("/users/me/devices/{deviceId}/sync")
    public SyncResultResponse sync(@AuthenticationPrincipal Jwt jwt, @PathVariable Long deviceId) {
        return devices.sync(AuthUser.from(jwt), deviceId);
    }

    @GetMapping("/users/me/metrics")
    public List<MetricResponse> metrics(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam(required = false) MetricType type,
                                        @RequestParam(required = false) OffsetDateTime from,
                                        @RequestParam(required = false) OffsetDateTime to,
                                        @RequestParam(required = false) Long bookingId) {
        return devices.metrics(AuthUser.from(jwt), type, from == null ? null : from.toInstant(),
                to == null ? null : to.toInstant(), bookingId);
    }
}
