package com.hyunwookshin.nudge

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Call
import java.util.Calendar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.recyclerview.widget.LinearSmoothScroller
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// For creating a new reminder
private val argDateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
class ReminderListFragment : Fragment(), Refreshable {

    private lateinit var reminderAdapter: ReminderAdapter
    private lateinit var miniCalendarAdapter: MiniCalendarAdapter
    private lateinit var todayText: TextView
    private lateinit var progressBar: ProgressBar
    private var reminderCallback: ReminderCallback? = null
    private lateinit var overflowButton: ImageButton
    // Manage state
    private lateinit var recyclerView: RecyclerView
    private var currentReminders: List<Reminder> = emptyList()
    private var miniCalAnchor: LocalDate = LocalDate.now()
    private lateinit var miniCalendarMonth: TextView
    private lateinit var miniCalPrev: ImageButton
    private lateinit var miniCalNext: ImageButton
    private lateinit var miniCalendarRv: RecyclerView
    private var calendarVisible = true
    private lateinit var calendarContainer: View

    // Pills
    private enum class Period { CURRENT, SEVEN_DAYS, THIRTY_DAYS }
    private var period: Period = Period.CURRENT
    private lateinit var periodPill: com.google.android.material.button.MaterialButton
    private lateinit var calendarTogglePill : com.google.android.material.button.MaterialButton

    // Empty state
    private lateinit var addReminderButton: com.google.android.material.button.MaterialButton
    private lateinit var shimmerLayout: com.facebook.shimmer.ShimmerFrameLayout
    private var isOffline = false
    private var isLoading = false

    // DB for caching
    private lateinit var db: AppDb

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_reminder_list, container, false)
        todayText = view.findViewById(R.id.todayText)

        val fmt = DateTimeFormatter.ofPattern("MMMM d, yyyy")
        todayText.text = "Today is " + LocalDate.now().format(fmt)
        progressBar = view.findViewById(R.id.progressBar)
        val calendar = Calendar.getInstance()

        overflowButton = view.findViewById(R.id.overflowButton)
        overflowButton.setOnClickListener { showOverflowMenu() }
        periodPill = view.findViewById(R.id.periodPill)
        renderPeriodPill()

        periodPill.setOnClickListener { anchor ->
            showPeriodMenu(anchor)
        }

        calendarTogglePill = view.findViewById(R.id.calendarTogglePill)
        renderCalendarTogglePill()
        calendarTogglePill.setOnClickListener {
            calendarVisible = !calendarVisible

            renderCalendarTogglePill()
            calendarContainer.visibility = if (calendarVisible) View.VISIBLE else View.GONE
        }
        return view
    }


    private fun renderPeriodPill() {
        periodPill.text = when (period) {
            Period.CURRENT -> "Current"
            Period.SEVEN_DAYS -> "Past 7 Days"
            Period.THIRTY_DAYS -> "Past 30 Days"
        }
    }

    private fun renderCalendarTogglePill() {
        calendarTogglePill.text = when (calendarVisible) {
            true -> "Hide Calendar"
            false -> "Show Calendar"
        }
    }

    private fun showPeriodMenu(anchor: View) {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "Current")
        popup.menu.add(0, 2, 1, "Past 7 Days")
        popup.menu.add(0, 3, 2, "Past 30 Days")

        popup.setOnMenuItemClickListener { item ->
            val newPeriod = when (item.itemId) {
                1 -> Period.CURRENT
                2 -> Period.SEVEN_DAYS
                3 -> Period.THIRTY_DAYS
                else -> period
            }

            if (newPeriod != period) {
                period = newPeriod
                renderPeriodPill()
                fetchReminders()
            }
            true
        }

        popup.show()
    }


    override fun refresh() {
        fetchReminders()
    }

    // Set MainActivity as the callback
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is ReminderCallback) {
            reminderCallback = context
        } else {
            throw RuntimeException("$context must implement ReminderCallback")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        shimmerLayout = view.findViewById(R.id.shimmerLayout)
        addReminderButton = view.findViewById(R.id.addReminderButton)
        addReminderButton.setOnClickListener {
            val fragment = ReminderFragment.newInstance()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit()
        }

        miniCalendarMonth = view.findViewById(R.id.miniCalendarMonth)
        miniCalPrev = view.findViewById(R.id.miniCalPrev)
        miniCalNext = view.findViewById(R.id.miniCalNext)

        miniCalPrev.setOnClickListener { jumpMonthMiniCalendar(-1) }
        miniCalNext.setOnClickListener { jumpMonthMiniCalendar(+1) }

        calendarContainer = view.findViewById(R.id.miniCalendarContainer)
        calendarContainer.visibility = if (calendarVisible) View.VISIBLE else View.GONE

        setupMiniCalendar(view)
        attachMonthSwipe(view)

        reminderAdapter = ReminderAdapter().apply {
            // Ensure that the Edit button is wired to the callback in MainActivity.
            reminderCallback?.let {
                setReminderCallback(it)
            }

            setOnReminderClick { reminder ->
                val date = reminderLocalDate(reminder) ?: return@setOnReminderClick

                // Reanchor mini calendar to this reminder’s date
                miniCalAnchor = date
                updateMiniCalendarMonth(miniCalAnchor)
                miniCalendarAdapter.submit(buildMiniCalendarDays(currentReminders, miniCalAnchor))
            }
        }
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = reminderAdapter
        progressBar.visibility = View.VISIBLE
        db = AppDb.get(requireContext())
        loadCachedReminders()
        fetchReminders()
    }

    override fun onDetach() {
        super.onDetach()
        reminderCallback = null
    }

    private fun loadCachedReminders() {
        viewLifecycleOwner.lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) {
                db.reminderDao().getAll().map { it.toDomain() }
            }

            if (cached.isNotEmpty()) {
                showLoading()

                currentReminders = cached.sortedBy { it.Time }
                reminderAdapter.setReminders(currentReminders)
                miniCalendarAdapter.submit(buildMiniCalendarDays(currentReminders, miniCalAnchor))
                updateMiniCalendarMonth(miniCalAnchor)

                progressBar.visibility = View.GONE
            }
        }
    }

    private fun fetchReminders() {
        showLoading()
        val apiService = ApiClient.getClient().create(ApiService::class.java)
        val call: Call<ReminderResponse> = when (period) {
            Period.CURRENT -> apiService.getReminders()
            Period.SEVEN_DAYS -> apiService.getReminders(include = 7)
            Period.THIRTY_DAYS -> apiService.getReminders(include = 30)
        }
        call.enqueue(object : Callback<ReminderResponse> {
            override fun onResponse(call: Call<ReminderResponse>, response: Response<ReminderResponse>) {
                if (!isAdded || view == null) return
                progressBar.visibility = View.GONE
                if (response.isSuccessful) {
                    val reminders = (response.body()?.reminders ?: emptyList())
                        .sortedBy { it.Time }
                    Log.d("ReminderListFragment", "Reminders fetched: ${reminders.size}")
                    showOffline(false)
                    currentReminders = reminders
                    reminderAdapter.setReminders(reminders)
                    updateEmptyState()
                    miniCalendarAdapter.submit(buildMiniCalendarDays(reminders, miniCalAnchor))
                    updateMiniCalendarMonth(miniCalAnchor)
                    // Also scroll to the anchor date
                    val idx = findFirstReminderIndexOnOrAfter(miniCalAnchor)
                    if (idx >= 0) {
                        recyclerView.post {
                            val offsetPx =
                                (recyclerView.resources.displayMetrics.density).toInt() // 32dp
                            recyclerView.smoothScrollToPositionWithOffset(idx, offsetPx)
                        }
                    }
                    // cache to DB
                    // Save to DB
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        db.reminderDao().replaceAll(reminders.map { it.toEntity() })
                    }
                    // Schedule reminders
                    ReminderScheduler.scheduleAll(requireContext(), reminders)

                } else {
                    showOffline(true)
                }
            }

            override fun onFailure(call: Call<ReminderResponse>, t: Throwable) {
                if (!isAdded || view == null) return
                progressBar.visibility = View.GONE
                showOffline(true)
            }
        })
    }


    private fun setupMiniCalendar(view: View) {
        miniCalendarRv = view.findViewById(R.id.miniCalendarRv)
        miniCalendarRv.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 7)

        miniCalendarAdapter = MiniCalendarAdapter(
            onDayClick = { clickedDate ->
                // 1) If the clicked date is on different month,
                // recenters the mini calendar so clicked date becomes the anchor
                // Only re-anchor if user clicked a day in a different month than what we're showing
                val sameMonth =
                    (clickedDate.year == miniCalAnchor.year) &&
                            (clickedDate.monthValue == miniCalAnchor.monthValue)

                if (!sameMonth) {
                    miniCalAnchor = clickedDate
                    updateMiniCalendarMonth(miniCalAnchor)
                    miniCalendarAdapter.submit(buildMiniCalendarDays(currentReminders, miniCalAnchor))
                }
                // 2) (optional) still scroll reminders list to that day
                val idx = findFirstReminderIndexOnOrAfter(clickedDate)
                if (idx >= 0) {
                    recyclerView.post {
                        val offsetPx =
                            (recyclerView.resources.displayMetrics.density).toInt() // 32dp
                        recyclerView.smoothScrollToPositionWithOffset(idx, offsetPx)
                    }
                }
            },
            onDayLongPress = { date, anchorView ->
                showDayMenu(date, anchorView)
            })
        miniCalendarRv.adapter = miniCalendarAdapter
    }

    private fun showDayMenu(date: LocalDate, anchor: View) {
        if (isLoading || isOffline) return
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, "+ Add Event")
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) {
                // Navigate to ReminderFragment with date prefilled
                val fragment = ReminderFragment.newInstanceForDate(date.format(argDateFmt))
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
                true
            } else false
        }
        popup.show()
    }

    private fun findFirstReminderIndexOnOrAfter(date: LocalDate): Int {
        for (i in currentReminders.indices) {
            val d = reminderLocalDate(currentReminders[i]) ?: continue
            if (!d.isBefore(date)) { // d >= date
                return i
            }
        }
        return -1
    }

    private fun reminderLocalDate(r: Reminder): LocalDate? {
        return runCatching {
            LocalDate.parse(r.Time.take(10)) // yyyy-MM-dd
        }.getOrNull()
    }

    private fun updateMiniCalendarMonth(anchor: LocalDate) {
        val fmt = DateTimeFormatter.ofPattern("MMMM yyyy")
        miniCalendarMonth.text = anchor.format(fmt)
    }

    private fun jumpMonthMiniCalendar(deltaMonths: Long) {
        // safe anchor: always the 10th
        val slide = 36f * resources.displayMetrics.density // ~36dp
        val dx = if (deltaMonths > 0) -slide else slide     // next month slides left

        animateMiniCalendar(dx = dx, dy = 0f) {
            miniCalAnchor = miniCalAnchor
                .plusMonths(deltaMonths)
                .withDayOfMonth(10)

            miniCalendarAdapter.submit(buildMiniCalendarDays(currentReminders, miniCalAnchor))
            updateMiniCalendarMonth(miniCalAnchor)
        }
    }

    private fun jumpWeeksMiniCalendar(deltaWeeks: Long) {
        val slide = 28f * resources.displayMetrics.density // ~28dp
        val dy = if (deltaWeeks > 0) -slide else slide     // forward slides up

        animateMiniCalendar(dx = 0f, dy = dy) {
            miniCalAnchor = miniCalAnchor.plusWeeks(deltaWeeks)

            miniCalendarAdapter.submit(buildMiniCalendarDays(currentReminders, miniCalAnchor))
            updateMiniCalendarMonth(miniCalAnchor)
        }
    }

    private fun animateMiniCalendar(
        dx: Float,
        dy: Float,
        update: () -> Unit
    ) {
        if (!::miniCalendarRv.isInitialized) {
            update()
            return
        }

        val v = miniCalendarRv
        val durOut = 120L
        val durIn = 140L

        // cancel any in-flight animation
        v.animate().cancel()

        v.animate()
            .translationX(dx)
            .translationY(dy)
            .alpha(0f)
            .setDuration(durOut)
            .withEndAction {
                // Update content while "hidden"
                update()

                // Start slightly from the other side and animate in
                v.translationX = -dx
                v.translationY = -dy
                v.alpha = 0f

                v.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(durIn)
                    .start()
            }
            .start()
    }


    private fun buildMiniCalendarDays(
        reminders: List<Reminder>,
        anchor: LocalDate
    ): List<DayState> {

        // Align to Sunday (S M T W T F S)
        val weekdayOffset = anchor.dayOfWeek.value % 7 // Sun=0
        val anchorWeekSunday = anchor.minusDays(weekdayOffset.toLong())

        // 4 rows (28 days). Put anchor week on row #2 (index 1):
        // Row 0: week before
        // Row 1: anchor week
        // Row 2: next week
        // Row 3: following week
        val start = anchorWeekSunday.minusDays(7)
        val end = start.plusDays(27)

        val agg = mutableMapOf<LocalDate, Triple<Int, Boolean, Boolean>>()

        for (r in reminders) {
            val date = runCatching { LocalDate.parse(r.Time.take(10)) }.getOrNull() ?: continue
            if (date.isBefore(start) || date.isAfter(end)) continue

            val prev = agg[date] ?: Triple(0, false, false)
            val count = prev.first + 1
            val hasHigh = prev.second || (r.Priority <= 1)
            val hasLow = prev.third || (r.Priority > 1)
            agg[date] = Triple(count, hasHigh, hasLow)
        }

        return (0..27).map { offset ->
            val d = start.plusDays(offset.toLong())
            val triple = agg[d] ?: Triple(0, false, false)
            DayState(date = d, count = triple.first, hasHigh = triple.second, hasLow = triple.third)
        }
    }

    private fun attachMonthSwipe(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.miniCalendarRv)
        val header = view.findViewById<View>(R.id.miniCalendarHeader)

        val detector = GestureDetector(requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {

                private val SWIPE_DISTANCE = 5   // px
                private val SWIPE_VELOCITY = 50  // px/sec

                override fun onDown(e: MotionEvent): Boolean = true

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false

                    val dx = e2.x - e1.x
                    val dy = e2.y - e1.y

                    val absDx = abs(dx)
                    val absDy = abs(dy)

                    // Horizontal month swipe
                    if (absDx > absDy) {
                        if (absDx < SWIPE_DISTANCE) return false
                        if (abs(velocityX) < SWIPE_VELOCITY) return false

                        if (dx < 0) jumpMonthMiniCalendar(+1) else jumpMonthMiniCalendar(-1)
                        return true
                    }

                    // Vertical 2-week swipe
                    if (absDy < SWIPE_DISTANCE) return false
                    if (abs(velocityY) < SWIPE_VELOCITY) return false

                    if (dy < 0) {
                        jumpWeeksMiniCalendar(+2)
                    } else {
                        jumpWeeksMiniCalendar(-2)
                    }
                    return true
                }
            })

        // addOnItemTouchListener intercepts events before any child cell gets them,
        // so the GestureDetector always sees ACTION_DOWN on the first swipe.
        rv.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                detector.onTouchEvent(e)
                return false // let clicks through to day cells
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })

        // Header has no children so setOnTouchListener is fine there.
        header.setOnTouchListener { _, ev ->
            detector.onTouchEvent(ev)
            true
        }
    }

    private fun showLoading() {
        val fmt = DateTimeFormatter.ofPattern("MMMM d, yyyy")
        val base = "Today is " + LocalDate.now().format(fmt)
        todayText.text = "$base   •   Loading…"
        overflowButton.isEnabled = false
        overflowButton.alpha = 0.35f
        reminderAdapter.setReadOnly(true)
        shimmerLayout.startShimmer()
        isLoading = true
    }

    private fun showOffline(offline: Boolean) {
        shimmerLayout.hideShimmer()
        isLoading = false
        isOffline = offline
        val fmt = DateTimeFormatter.ofPattern("MMMM d, yyyy")
        val base = "Today is " + LocalDate.now().format(fmt)
        todayText.text = if (offline) "$base   •   Offline" else base
        overflowButton.isEnabled = !offline
        overflowButton.alpha = if (offline) 0.35f else 1.0f
        reminderAdapter.setReadOnly(offline)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        addReminderButton.visibility =
            if (currentReminders.isEmpty() && !isOffline) View.VISIBLE else View.GONE
    }

    private fun showOverflowMenu() {
        val popup = androidx.appcompat.widget.PopupMenu(requireContext(), overflowButton)
        popup.menuInflater.inflate(R.menu.reminder_overflow_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_add_event -> {
                    val fragment = ReminderFragment.newInstance()
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .addToBackStack(null)
                        .commit()
                    true
                }


                R.id.menu_account -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, AccountFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }

                R.id.menu_toggle_mini_calendar -> {
                    calendarVisible = !calendarVisible
                    renderCalendarTogglePill()
                    calendarContainer.visibility = if (calendarVisible) View.VISIBLE else View.GONE
                    true
                }

                R.id.menu_logout -> {
                    AuthStore.clear(requireContext())
                    // Clear back stack and go to Login
                    parentFragmentManager.popBackStack(
                        null,
                        androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, LoginFragment())
                        .commit()
                    lifecycleScope.launch(Dispatchers.IO) {
                        val db = AppDb.get(requireContext())
                        val reminders = db.reminderDao().getAll().map { it.toDomain() }
                        withContext(Dispatchers.Main) {
                            if (!isAdded) return@withContext
                            reminders.forEach {
                                if (getContext() != null) {
                                    ReminderScheduler.cancelReminder(requireContext(), it.Id)
                                    FiredStore.clear(requireContext(), it.Id)
                                }
                            }
                        }

                        db.reminderDao().clearAll()
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun RecyclerView.smoothScrollToPositionWithOffset(position: Int, offsetPx: Int) {
        val lm = layoutManager as? LinearLayoutManager ?: run {
            smoothScrollToPosition(position)
            return
        }

        val scroller = object : LinearSmoothScroller(context) {
            override fun getVerticalSnapPreference(): Int = SNAP_TO_START

            override fun calculateDyToMakeVisible(view: View, snapPreference: Int): Int {
                // default snap-to-start dy, then apply your extra offset
                return super.calculateDyToMakeVisible(view, snapPreference) - offsetPx
            }
        }

        scroller.targetPosition = position
        lm.startSmoothScroll(scroller)
    }

}
