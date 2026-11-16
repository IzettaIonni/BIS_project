package kz.gymbooking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import kz.gymbooking.domain.Booking;
import kz.gymbooking.domain.Device;
import kz.gymbooking.domain.DeviceStatus;
import kz.gymbooking.domain.GymClass;
import kz.gymbooking.domain.Role;
import kz.gymbooking.support.Fixtures;
import kz.gymbooking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

class DevicesIT extends IntegrationTest {

    private Device connected() {
        Device d = Fixtures.device();
        d.setProvider("stub");
        when(devices.findById(1L)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void providersArePublic() throws Exception {
        mvc.perform(get("/device-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].code").value("stub"))
                .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    void connectStub() throws Exception {
        mvc.perform(post("/users/me/devices").with(as(17, Role.CLIENT)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("provider", "stub"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONNECTED"))
                .andExpect(jsonPath("$.name").value("Stub device (fake data)"));
    }

    @Test
    void connectDisabledProviderGives422() throws Exception {
        mvc.perform(post("/users/me/devices").with(as(17, Role.CLIENT)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("provider", "garmin"))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("provider"));
    }

    @Test
    void disconnectOwn() throws Exception {
        connected();
        mvc.perform(delete("/users/me/devices/1").with(as(17, Role.CLIENT))).andExpect(status().isNoContent());
        ArgumentCaptor<Device> saved = ArgumentCaptor.forClass(Device.class);
        verify(devices).save(saved.capture());
        org.junit.jupiter.api.Assertions.assertEquals(DeviceStatus.DISCONNECTED, saved.getValue().getStatus());
    }

    @Test
    void disconnectForeignGives404() throws Exception {
        connected();
        mvc.perform(delete("/users/me/devices/1").with(as(99, Role.CLIENT))).andExpect(status().isNotFound());
        verify(devices, never()).save(any());
    }

    @Test
    void firstSyncImports24Hours() throws Exception {
        connected();
        mvc.perform(post("/users/me/devices/1/sync").with(as(17, Role.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(72))
                .andExpect(jsonPath("$.lastSyncAt").value("2026-11-16T10:00:00Z"));
    }

    @Test
    void syncSinceLastSync() throws Exception {
        connected().setLastSyncAt(NOW.minus(Duration.ofHours(2)));
        mvc.perform(post("/users/me/devices/1/sync").with(as(17, Role.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(6));
    }

    @Test
    void syncWindowCappedAtSevenDays() throws Exception {
        connected().setLastSyncAt(NOW.minus(Duration.ofDays(30)));
        mvc.perform(post("/users/me/devices/1/sync").with(as(17, Role.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(7 * 24 * 3));
    }

    @Test
    void syncWithFutureLastSyncImportsNothing() throws Exception {
        Instant future = NOW.plus(Duration.ofHours(3));
        connected().setLastSyncAt(future);
        mvc.perform(post("/users/me/devices/1/sync").with(as(17, Role.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.lastSyncAt").value("2026-11-16T13:00:00Z"));
        verify(devices, never()).save(any());
        verify(metrics, never()).saveAll(any());
    }

    @Test
    void syncDisconnectedGives404() throws Exception {
        connected().setStatus(DeviceStatus.DISCONNECTED);
        mvc.perform(post("/users/me/devices/1/sync").with(as(17, Role.CLIENT))).andExpect(status().isNotFound());
    }

    @Test
    void metricsForBookingUseClassWindow() throws Exception {
        Booking b = Fixtures.booking();
        GymClass c = Fixtures.gymClass();
        when(bookings.findById(1L)).thenReturn(Optional.of(b));
        when(classes.findById(42L)).thenReturn(Optional.of(c));
        when(metrics.search(any(), any(), any(), any())).thenReturn(List.of(Fixtures.metric()));
        mvc.perform(get("/users/me/metrics").param("bookingId", "1").with(as(17, Role.CLIENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("HEART_RATE"));
        verify(metrics).search(eq(17L), eq(null), eq(c.getStartTime()), eq(c.endTime()));
    }

    @Test
    void metricsForForeignBookingGives404() throws Exception {
        when(bookings.findById(1L)).thenReturn(Optional.of(Fixtures.booking()));
        mvc.perform(get("/users/me/metrics").param("bookingId", "1").with(as(99, Role.CLIENT)))
                .andExpect(status().isNotFound());
    }
}
