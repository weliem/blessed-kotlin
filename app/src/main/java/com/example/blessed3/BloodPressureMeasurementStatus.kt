package com.example.blessed3

class BloodPressureMeasurementStatus internal constructor(measurementStatus: UInt) {
    /**
     * Body Movement Detected
     */
    val isBodyMovementDetected: Boolean
    /**
     * Cuff is too loose
     */
    val isCuffTooLoose: Boolean
    /**
     * Irregular pulse detected
     */
    val isIrregularPulseDetected: Boolean
    /**
     * Pulse is not in normal range
     */
    val isPulseNotInRange: Boolean
    /**
     * Improper measurement position
     */
    val isImproperMeasurementPosition: Boolean

    init {
        isBodyMovementDetected = measurementStatus and 0x0001u > 0u
        isCuffTooLoose = measurementStatus and 0x0002u > 0u
        isIrregularPulseDetected = measurementStatus and 0x0004u > 0u
        isPulseNotInRange = measurementStatus and 0x0008u > 0u
        isImproperMeasurementPosition = measurementStatus and 0x0020u > 0u
    }
}