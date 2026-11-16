package kz.gymbooking.repository;

import java.util.List;
import java.util.Optional;

import kz.gymbooking.domain.Device;
import kz.gymbooking.domain.DeviceStatus;

public interface DeviceRepository {

    Optional<Device> findById(Long id);

    Device save(Device device);

    List<Device> findByUserIdAndStatusNot(Long userId, DeviceStatus status);
}
