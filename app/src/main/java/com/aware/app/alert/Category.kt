package com.aware.app.alert

enum class Category { EMERGENCY, TRAFFIC, HOME, PEOPLE, ANIMALS }

enum class SoundType(val category: Category, val displayName: String, val alerts: Boolean = true) {
    SIREN(Category.EMERGENCY, "Siren"),
    SMOKE_ALARM(Category.EMERGENCY, "Smoke alarm"),
    GLASS_BREAK(Category.EMERGENCY, "Glass breaking"),
    SCREAM(Category.EMERGENCY, "Scream"),
    CAR_HORN(Category.TRAFFIC, "Car horn"),
    VEHICLE(Category.TRAFFIC, "Vehicle"),
    BICYCLE_BELL(Category.TRAFFIC, "Bicycle bell"),
    DOORBELL(Category.HOME, "Doorbell"),
    KNOCK(Category.HOME, "Knock"),
    PHONE_RING(Category.HOME, "Phone ringing"),
    APPLIANCE_BEEP(Category.HOME, "Appliance beep"),
    WATER_RUNNING(Category.HOME, "Running water"),
    BABY_CRY(Category.PEOPLE, "Baby crying"),
    SPEECH(Category.PEOPLE, "Speech", alerts = false),
    DOG_BARK(Category.ANIMALS, "Dog barking"),
}
