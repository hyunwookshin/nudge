package com.hyunwookshin.nudge

import android.os.Parcel
import android.os.Parcelable

data class Reminder(
    val Title: String = "",
    val Description: String = "",
    val Date: String = "",
    val Time: String = "",
    var Id: String = "",
    val Link: String = "",
    val Priority: Int = 2,
    val Key: String = "",
    val Snooze: Int = 0,
    val Read: String = "",
    val Repeat: Int = 0,
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(Title)
        parcel.writeString(Description)
        parcel.writeString(Date)
        parcel.writeString(Time)
        parcel.writeString(Id)
        parcel.writeString(Link)
        parcel.writeInt(Priority)
        parcel.writeString(Key)
        parcel.writeInt(Snooze)
        parcel.writeString(Read)
        parcel.writeInt(Repeat)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Reminder> {
        override fun createFromParcel(parcel: Parcel): Reminder {
            return Reminder(parcel)
        }

        override fun newArray(size: Int): Array<Reminder?> {
            return arrayOfNulls(size)
        }
    }
}
