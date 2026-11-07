package com.hyunwookshin.nudge

import android.os.Parcel
import android.os.Parcelable

/**
 * A reusable reminder blueprint: the content of a reminder without a date, time or
 * recurrence. Starting an event from a template copies these fields into a new Reminder.
 */
data class Template(
    val Title: String = "",
    val Description: String = "",
    var Id: String = "",
    val Link: String = "",
    val Priority: Int = 2,
    val Key: String = "",
    // Local-only flag, like Reminder.isPending: saved while offline and not synced yet.
    val isPending: Boolean = false,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readByte() != 0.toByte(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(Title)
        parcel.writeString(Description)
        parcel.writeString(Id)
        parcel.writeString(Link)
        parcel.writeInt(Priority)
        parcel.writeString(Key)
        parcel.writeByte(if (isPending) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    /** A new (unsaved) reminder with this template's content; the date is up to the user. */
    fun toNewReminder(date: String = ""): Reminder = Reminder(
        Title = Title,
        Description = Description,
        Date = date,
        Link = Link,
        Priority = Priority,
    )

    companion object CREATOR : Parcelable.Creator<Template> {
        override fun createFromParcel(parcel: Parcel): Template {
            return Template(parcel)
        }

        override fun newArray(size: Int): Array<Template?> {
            return arrayOfNulls(size)
        }

        /** Template from an event's content (Save as Template on the edit screen). */
        fun fromReminder(reminder: Reminder): Template = Template(
            Title = reminder.Title,
            Description = reminder.Description,
            Link = reminder.Link,
            Priority = reminder.Priority,
            Key = reminder.Key,
        )
    }
}
