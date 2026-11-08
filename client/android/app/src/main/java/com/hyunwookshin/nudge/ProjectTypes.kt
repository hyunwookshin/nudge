package com.hyunwookshin.nudge

/**
 * The categories a project can have. A type decides the project's icon, color and calendar band
 * (the color comes from CardStyle, like it does for events); "None" is a plain gray project.
 */
object ProjectTypes {
    data class Type(val key: String, val label: String, val iconRes: Int?)

    val all: List<Type> = listOf(
        Type("", "None (gray)", null),
        Type("trip", "Trip", R.drawable.ic_ctx_flight),
        Type("document", "Document", R.drawable.ic_ctx_description),
        Type("car", "Car", R.drawable.ic_ctx_car),
        Type("food", "Food", R.drawable.ic_ctx_restaurant),
        Type("cafe", "Coffee", R.drawable.ic_ctx_cafe),
        Type("home", "Home", R.drawable.ic_ctx_home),
        Type("clothes", "Clothes", R.drawable.ic_ctx_tshirt),
        Type("hotel", "Hotel", R.drawable.ic_ctx_hotel),
        Type("reservation", "Reservation", R.drawable.ic_ctx_reservation),
        Type("lesson", "Lessons", R.drawable.ic_ctx_lesson),
        Type("meeting", "Meeting", R.drawable.ic_ctx_people),
        Type("call", "Call", R.drawable.ic_ctx_call),
        Type("presentation", "Presentation", R.drawable.ic_ctx_laptop),
        Type("dental", "Dental", R.drawable.ic_ctx_tooth),
        Type("eye", "Eye", R.drawable.ic_ctx_eye),
        Type("beauty", "Hair & Skin", R.drawable.ic_ctx_lotion),
    )

    fun byKey(key: String): Type = all.firstOrNull { it.key == key } ?: all.first()

    fun iconFor(key: String): Int? = byKey(key).iconRes
}
