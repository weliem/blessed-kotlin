package com.example.blessed3

enum class TemperatureType(val value: UInt) {
    Unknown(0u), Armpit(1u), Body(2u), Ear(3u), Finger(4u), GastroIntestinalTract(5u), Mouth(6u), Rectum(7u), Toe(8u), Tympanum(9u);

    companion object {
        fun fromValue(value: UInt): TemperatureType {
            for (type in values()) {
                if (type.value == value) return type
            }
            return Unknown
        }
    }
}