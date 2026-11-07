package com.hyunwookshin.nudge

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val link: String,
    val priority: Int,
    val isPending: Boolean = false,
    val pendingKey: String = "",
)

// Entity -> Domain
fun TemplateEntity.toDomain(): Template = Template(
    Title = title,
    Description = description,
    Id = id,
    Link = link,
    Priority = priority,
    Key = "",
    isPending = isPending,
)

// Domain -> Entity
fun Template.toEntity(): TemplateEntity = TemplateEntity(
    id = Id,
    title = Title,
    description = Description,
    link = Link,
    priority = Priority,
    isPending = false,
    pendingKey = "",
)
