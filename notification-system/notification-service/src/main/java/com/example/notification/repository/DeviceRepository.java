package com.example.notification.repository;

import com.example.notification.domain.Device;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {

  List<Device> findByUserId(Long userId);
}
