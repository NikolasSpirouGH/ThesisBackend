package com.cloud_ml_app_thesis.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class DateUtil {

    /**
     * Parse date/datetime string to LocalDateTime.
     * Supports both date-only format (YYYY-MM-DD) and full datetime format.
     *
     * @param dateString the date or datetime string to parse
     * @param isStartOfDay if true and date-only format, returns start of day (00:00:00);
     *                     if false and date-only format, returns end of day (23:59:59.999999999)
     * @return parsed LocalDateTime
     */
    public LocalDateTime parseDateTime(String dateString, boolean isStartOfDay) {
        try {
            // Try parsing as date-only format (YYYY-MM-DD)
            LocalDate date = LocalDate.parse(dateString);
            return isStartOfDay ? date.atStartOfDay() : date.atTime(23, 59, 59, 999999999);
        } catch (Exception e1) {
            // If that fails, try parsing as full LocalDateTime
            return LocalDateTime.parse(dateString);
        }
    }
}
