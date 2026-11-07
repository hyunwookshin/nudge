package com.hyunwookshin.nudge

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar

/** Template cards: tap to start a new event from one, swipe left or right to delete (with Undo). */
class TemplateAdapter(
    private val onUse: (Template) -> Unit,
    private val onDelete: (Template) -> Unit,
) : RecyclerView.Adapter<TemplateAdapter.TemplateViewHolder>() {

    private var templates: List<Template> = listOf()
    private var isOffline = false

    fun setTemplates(templates: List<Template>) {
        this.templates = templates
        notifyDataSetChanged()
    }

    fun setOfflineMode(offline: Boolean) {
        isOffline = offline
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TemplateViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_template, parent, false)
        return TemplateViewHolder(view)
    }

    override fun onBindViewHolder(holder: TemplateViewHolder, position: Int) {
        holder.bind(templates[position])
    }

    override fun getItemCount(): Int = templates.size

    inner class TemplateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.title)
        private val description: TextView = itemView.findViewById(R.id.description)
        private val pendingBadge: TextView = itemView.findViewById(R.id.pendingBadge)
        private val accentBar: View = itemView.findViewById(R.id.accentBar)
        private val cardContent: View = itemView.findViewById(R.id.cardContent)
        private val contextIcon: ImageView = itemView.findViewById(R.id.contextIcon)

        fun bind(template: Template) {
            title.text = template.Title
            description.text = template.Description
            pendingBadge.visibility = if (template.isPending) View.VISIBLE else View.GONE

            val ctx = itemView.context
            val iconRes = CardStyle.contextIconFor(template.Title, template.Description)
            val accentColor = CardStyle.accentColor(ctx, iconRes, template.Priority)
            // same 9dp accent column as reminder cards, left 5dp painted
            accentBar.background = InsetDrawable(ColorDrawable(accentColor), 0, 0,
                (4 * ctx.resources.displayMetrics.density).toInt(), 0)
            cardContent.background = CardStyle.cardArt(ctx, iconRes, accentColor, template.Id.hashCode().toLong())
            CardStyle.styleIcon(contextIcon, iconRes, accentColor)

            itemView.setOnClickListener { onUse(template) }
        }
    }

    /** Swipe to delete, like reminder cards: removed at once, deleted for real when the Undo snackbar goes away. */
    fun attachSwipeToDelete(recyclerView: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun getSwipeThreshold(vh: RecyclerView.ViewHolder) = 0.35f

            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int =
                if (isOffline) 0 else super.getSwipeDirs(rv, vh)

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val position = vh.adapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val template = templates[position]
                templates = templates.toMutableList().apply { removeAt(position) }
                notifyItemRemoved(position)
                Snackbar.make(recyclerView, "Template deleted", Snackbar.LENGTH_LONG)
                    .setAction("Undo") {
                        templates = templates.toMutableList().apply { add(minOf(position, size), template) }
                        notifyItemInserted(position)
                        recyclerView.scrollToPosition(position)
                    }
                    .addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(bar: Snackbar?, event: Int) {
                            if (event != DISMISS_EVENT_ACTION) onDelete(template)
                        }
                    })
                    .show()
            }

            override fun onChildDraw(
                c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                val item = vh.itemView
                if (dX != 0f) {
                    val density = rv.resources.displayMetrics.density
                    val background = GradientDrawable().apply {
                        cornerRadius = 12f * density
                        setColor(Color.parseColor("#E53935"))
                    }
                    background.setBounds(item.left, item.top, item.right, item.bottom)
                    c.save()
                    if (dX > 0) c.clipRect(item.left.toFloat(), item.top.toFloat(), item.left + dX, item.bottom.toFloat())
                    else c.clipRect(item.right + dX, item.top.toFloat(), item.right.toFloat(), item.bottom.toFloat())
                    background.draw(c)
                    ContextCompat.getDrawable(rv.context, R.drawable.ic_delete)?.mutate()?.let { icon ->
                        icon.setTint(Color.WHITE)
                        val inset = (24 * density).toInt()
                        val top = item.top + (item.height - icon.intrinsicHeight) / 2
                        val left = if (dX > 0) item.left + inset else item.right - inset - icon.intrinsicWidth
                        icon.setBounds(left, top, left + icon.intrinsicWidth, top + icon.intrinsicHeight)
                        icon.draw(c)
                    }
                    c.restore()
                }
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
    }
}
