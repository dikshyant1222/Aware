package com.aware.app.alert

object CategoryMapper {
    // Exact category names from yamnet.tflite's embedded yamnet_label_list.txt.
    val mappings: Map<String, SoundType> = mapOf(
        "Siren" to SoundType.SIREN,
        "Civil defense siren" to SoundType.SIREN,
        "Police car (siren)" to SoundType.SIREN,
        "Ambulance (siren)" to SoundType.SIREN,
        "Fire engine, fire truck (siren)" to SoundType.SIREN,
        "Emergency vehicle" to SoundType.SIREN,
        "Smoke detector, smoke alarm" to SoundType.SMOKE_ALARM,
        "Fire alarm" to SoundType.SMOKE_ALARM,
        "Shatter" to SoundType.GLASS_BREAK,
        "Screaming" to SoundType.SCREAM,
        "Vehicle horn, car horn, honking" to SoundType.CAR_HORN,
        "Air horn, truck horn" to SoundType.CAR_HORN,
        "Toot" to SoundType.CAR_HORN,
        "Car" to SoundType.VEHICLE,
        "Truck" to SoundType.VEHICLE,
        "Motor vehicle (road)" to SoundType.VEHICLE,
        "Traffic noise, roadway noise" to SoundType.VEHICLE,
        "Bicycle bell" to SoundType.BICYCLE_BELL,
        "Doorbell" to SoundType.DOORBELL,
        "Ding-dong" to SoundType.DOORBELL,
        "Knock" to SoundType.KNOCK,
        "Telephone bell ringing" to SoundType.PHONE_RING,
        "Ringtone" to SoundType.PHONE_RING,
        "Beep, bleep" to SoundType.APPLIANCE_BEEP,
        "Buzzer" to SoundType.APPLIANCE_BEEP,
        "Water tap, faucet" to SoundType.WATER_RUNNING,
        "Sink (filling or washing)" to SoundType.WATER_RUNNING,
        "Baby cry, infant cry" to SoundType.BABY_CRY,
        // YAMNet splits one cry across these two labels, and a real cry often scores higher on the second.
        "Crying, sobbing" to SoundType.BABY_CRY,
        "Speech" to SoundType.SPEECH,
        "Conversation" to SoundType.SPEECH,
        "Narration, monologue" to SoundType.SPEECH,
        "Child speech, kid speaking" to SoundType.SPEECH,
        "Dog" to SoundType.DOG_BARK,
        "Bark" to SoundType.DOG_BARK,
        "Bow-wow" to SoundType.DOG_BARK,
        "Yip" to SoundType.DOG_BARK,
        "Howl" to SoundType.DOG_BARK,
        "Cat" to SoundType.CAT,
        "Meow" to SoundType.CAT,
        "Caterwaul" to SoundType.CAT,
        "Bird vocalization, bird call, bird song" to SoundType.BIRD,
        "Chirp, tweet" to SoundType.BIRD,
        "Squawk" to SoundType.BIRD,
        "Crow" to SoundType.BIRD,
        "Owl" to SoundType.BIRD,
    )

    // Alarm includes alarm clocks; Glass includes clinks; Tap includes ordinary impacts.
    // Telephone includes dialing, and Microwave oven includes its motor, not just beeps.
    // Leave these broad labels unmapped rather than implying a more specific event.
    fun map(labelName: String): SoundType? = mappings[labelName]

    fun minimumScore(type: SoundType): Float = when (type) {
        SoundType.SIREN, SoundType.SMOKE_ALARM, SoundType.SCREAM -> 0.22f
        SoundType.GLASS_BREAK -> 0.35f
        // Birdsong is nearly constant outdoors, so it needs a clearer call than other animals.
        SoundType.BIRD -> 0.40f
        SoundType.VEHICLE, SoundType.WATER_RUNNING, SoundType.APPLIANCE_BEEP -> 0.40f
        SoundType.SPEECH -> 0.40f
        else -> 0.30f
    }

    // Consecutive ~0.5 s windows needed before alerting. Transient sounds last about 1 s,
    // so they need fewer windows than sustained ones.
    fun requiredWindows(type: SoundType): Int = when (type) {
        SoundType.GLASS_BREAK -> 1
        SoundType.SIREN, SoundType.SMOKE_ALARM, SoundType.SCREAM -> 2
        SoundType.KNOCK, SoundType.DOORBELL, SoundType.CAR_HORN, SoundType.BICYCLE_BELL -> 2
        SoundType.BABY_CRY, SoundType.DOG_BARK, SoundType.CAT -> 2
        else -> 3
    }

    // The recent windows a required hit may fall in. Crying and barking come in bursts with gaps,
    // so their hits needn't be back to back.
    fun windowSpan(type: SoundType): Int = when (type) {
        SoundType.BABY_CRY, SoundType.DOG_BARK, SoundType.CAT -> requiredWindows(type) + 3
        else -> requiredWindows(type) + 1
    }
}
