package com.hyunwookshin.nudge

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.AdapterView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * A row of single-choice chips with the small API of a Spinner (selectedItemPosition, setSelection,
 * onItemSelectedListener), so the editor's logic reads the same as when it used spinners.
 */
class ChoiceChips @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ChipGroup(context, attrs) {

    var onItemSelectedListener: AdapterView.OnItemSelectedListener? = null

    init {
        isSingleSelection = true
        isSelectionRequired = true
        setOnCheckedStateChangeListener { _, _ ->
            val position = selectedItemPosition
            if (position >= 0) onItemSelectedListener?.onItemSelected(null, getChildAt(position), position, position.toLong())
        }
    }

    /** Replaces the chips and selects the first one (like a Spinner does). */
    fun setEntries(entries: List<String>) {
        removeAllViews()
        val inflater = LayoutInflater.from(context)
        entries.forEach { label ->
            val chip = inflater.inflate(R.layout.chip_choice, this, false) as Chip
            chip.id = generateViewId()
            chip.text = label
            addView(chip)
        }
        setSelection(0)
    }

    val selectedItemPosition: Int
        get() = (0 until childCount).indexOfFirst { (getChildAt(it) as? Chip)?.isChecked == true }

    fun setSelection(position: Int) {
        (getChildAt(position) as? Chip)?.isChecked = true
    }
}
