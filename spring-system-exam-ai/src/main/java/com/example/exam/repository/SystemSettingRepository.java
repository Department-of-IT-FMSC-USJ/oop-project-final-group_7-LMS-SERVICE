package com.example.exam.repository;

import com.example.exam.entity.SettingCategory;
import com.example.exam.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {
    Optional<SystemSetting> findBySettingKey(String settingKey);
    List<SystemSetting> findByCategory(SettingCategory category);
}
