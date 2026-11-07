package com.hyunwookshin.nudge

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

/**
 * Category styling shared by reminder and template cards: the context icon picked from
 * keywords, the accent color, and the pajama-print background.
 */
object CardStyle {

    private val contextIconRules: List<Pair<Set<String>, Int>> = listOf(
        setOf("flight", "flights", "airport", "fly", "flying", "trip") to R.drawable.ic_ctx_flight,
        setOf("car", "dmv", "drive", "driving", "roadtrip") to R.drawable.ic_ctx_car,
        setOf("dentist", "dental") to R.drawable.ic_ctx_tooth,
        setOf("eye", "eyes", "optometry", "optometrist") to R.drawable.ic_ctx_eye,
        setOf("dinner", "lunch", "breakfast", "brunch", "supper", "food", "meal", "meals",
            "eat", "eating", "restaurant", "dining") to R.drawable.ic_ctx_restaurant,
        setOf("coffee", "cafe", "café") to R.drawable.ic_ctx_cafe,
        setOf("presentation", "talk", "seminar", "conference") to R.drawable.ic_ctx_laptop,
        setOf("meeting", "meet", "friend", "friends") to R.drawable.ic_ctx_people,
        setOf("phone", "mentor", "mentoring", "call") to R.drawable.ic_ctx_call,
        setOf("submit", "application", "apply", "form", "tax", "taxes", "irs",
            "document", "documents", "paperwork") to R.drawable.ic_ctx_description,
        setOf("property") to R.drawable.ic_ctx_home,
        setOf("clothes", "clothing", "laundry") to R.drawable.ic_ctx_tshirt,
        setOf("hotel", "stay") to R.drawable.ic_ctx_hotel,
        setOf("reservation", "reservations") to R.drawable.ic_ctx_reservation,
        setOf("lesson", "lessons") to R.drawable.ic_ctx_lesson,
    )

    private val contextAccents: Map<Int, Int> = mapOf(
        R.drawable.ic_ctx_cafe to R.color.ctx_cafe_accent,
        R.drawable.ic_ctx_call to R.color.ctx_call_accent,
        R.drawable.ic_ctx_people to R.color.ctx_people_accent,
        R.drawable.ic_ctx_flight to R.color.ctx_flight_accent,
        R.drawable.ic_ctx_restaurant to R.color.ctx_food_accent,
    )

    // "Pajama print" motifs scattered behind the card content for the playful categories.
    // Serious ones (appointments, meetings, calls, documents, presentations) stay plain.
    private val contextMotifs: Map<Int, List<Int>> = mapOf(
        R.drawable.ic_ctx_flight to listOf(R.drawable.art_flight_plane, R.drawable.art_flight_ticket, R.drawable.art_flight_passport, R.drawable.art_flight_glasses),
        R.drawable.ic_ctx_car to listOf(R.drawable.art_car_car, R.drawable.art_key, R.drawable.art_car_sign, R.drawable.art_car_cone),
        R.drawable.ic_ctx_restaurant to listOf(R.drawable.art_food_cutlery, R.drawable.art_food_plate, R.drawable.art_food_bowl, R.drawable.art_food_glass),
        R.drawable.ic_ctx_cafe to listOf(R.drawable.art_cafe_mug, R.drawable.art_cafe_bean, R.drawable.art_cafe_muffin),
        R.drawable.ic_ctx_home to listOf(R.drawable.art_home_house, R.drawable.art_key, R.drawable.art_home_tree),
        R.drawable.ic_ctx_tshirt to listOf(R.drawable.art_clothes_shirt, R.drawable.art_clothes_hanger, R.drawable.art_clothes_sock),
        R.drawable.ic_ctx_hotel to listOf(R.drawable.art_hotel_bed, R.drawable.art_hotel_card, R.drawable.art_hotel_case, R.drawable.art_hotel_moon),
        R.drawable.ic_ctx_reservation to listOf(R.drawable.art_res_calendar, R.drawable.art_res_bell, R.drawable.art_res_clock),
        R.drawable.ic_ctx_lesson to listOf(R.drawable.art_lesson_book, R.drawable.art_pen, R.drawable.art_lesson_cap, R.drawable.art_lesson_apple),
    )

    /** Category icon for a card: a project uses its type, an event its keywords. */
    fun iconFor(reminder: Reminder): Int? =
        if (reminder.isProject) ProjectTypes.iconFor(reminder.ProjectType)
        else contextIconFor(reminder.Title, reminder.Description)

    /** Accent for a card; a project without a color of its type is gray (priority is shown on the date chip). */
    fun accentFor(context: Context, reminder: Reminder, iconRes: Int?): Int =
        accentColor(context, iconRes, if (reminder.isProject) 2 else reminder.Priority)

    // Title wins over description so a stray word in the notes doesn't override the headline.
    fun contextIconFor(title: String, description: String): Int? {
        for (text in listOf(title, description)) {
            val words = text.lowercase()
                .replace(Regex("road\\s+trip"), "roadtrip")
                .split(Regex("[^\\p{L}]+"))
                .toSet()
            contextIconRules.firstOrNull { (keys, _) -> keys.any(words::contains) }
                ?.let { return it.second }
        }
        return null
    }

    // real-life color for some categories (cafe brown, call green, ...), else the priority color;
    // each has a day and a night shade (deeper on light, brighter on dark)
    fun accentColor(context: Context, iconRes: Int?, priority: Int): Int =
        ContextCompat.getColor(
            context,
            contextAccents[iconRes]
                ?: if (priority <= 1) R.color.high_priority_accent else R.color.low_priority_accent
        )

    /** Shows the category icon in a soft accent-colored circle, or hides it. */
    fun styleIcon(view: ImageView, iconRes: Int?, accentColor: Int) {
        if (iconRes == null) {
            view.visibility = View.GONE
            return
        }
        view.setImageResource(iconRes)
        view.imageTintList = ColorStateList.valueOf(accentColor)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ColorUtils.setAlphaComponent(accentColor, 0x33))
        }
        view.visibility = View.VISIBLE
    }

    /** Pajama print for playful categories (null for the rest), seeded so a card keeps its layout. */
    fun cardArt(context: Context, iconRes: Int?, accentColor: Int, seed: Long): Drawable? {
        val motifRes = iconRes?.let(contextMotifs::get) ?: return null
        val motifs = motifRes.mapNotNull { res ->
            ContextCompat.getDrawable(context, res)?.mutate()?.apply { setTint(accentColor) }
        }
        return MotifPatternDrawable(motifs, seed, context.resources.displayMetrics.density,
            motifAlpha = 40)   // ~16%: print, not decoration
    }
}
