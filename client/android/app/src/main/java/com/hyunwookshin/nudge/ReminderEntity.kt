package com.hyunwookshin.nudge

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val date: String,
    val time: String,
    val link: String,
    val priority: Int,
    val snooze: Int,
    val read: String,
    val isPending: Boolean = false,
    val pendingKey: String = "",
)

// Entity -> Domain
fun ReminderEntity.toDomain(): Reminder = Reminder(
    Title = title,
    Description = description,
    Date = date,
    Time = time,
    Id = id,
    Link = link,
    Priority = priority,
    Key = "",
    Snooze = snooze,
    Read = read
)

// Domain -> Entity
fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    id = Id,
    title = Title,
    description = Description,
    date = Time.take(10),
    time = Time,
    link = Link,
    priority = Priority,
    snooze = Snooze,
    read = Read,
    isPending = false,
    pendingKey = "",
)
