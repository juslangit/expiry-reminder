package com.expiryreminder.app

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ItemAdapter
    private lateinit var emptyView: View
    private var items: List<Item> = emptyList()

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        emptyView = findViewById(R.id.empty)
        adapter = ItemAdapter { showEditor(it) }
        findViewById<RecyclerView>(R.id.list).adapter = adapter
        findViewById<View>(R.id.add).setOnClickListener { showEditor(null) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload every time: a reminder may have updated the file, and "days left" changes daily.
        showItems(ItemStore.load(this))
        // Unchanged items are skipped by the diff, but their "days left" text may be stale.
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
    }

    private fun showItems(newItems: List<Item>) {
        items = newItems.sortedBy { it.expiry }
        adapter.submitList(items)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun saveItems(newItems: List<Item>) {
        ItemStore.save(this, newItems)
        showItems(newItems)
    }

    private fun showEditor(existing: Item?) {
        val view = layoutInflater.inflate(R.layout.dialog_item, null)
        val nameLayout = view.findViewById<TextInputLayout>(R.id.name_layout)
        val nameInput = view.findViewById<TextInputEditText>(R.id.name)
        val categorySpinner = view.findViewById<Spinner>(R.id.category)
        val dateButton = view.findViewById<Button>(R.id.date)

        val categories = resources.getStringArray(R.array.categories)
        categorySpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        var date = existing?.expiry ?: LocalDate.now().plusMonths(1)
        dateButton.text = Reminders.formatDate(date)
        dateButton.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    date = LocalDate.of(year, month + 1, day)
                    dateButton.text = Reminders.formatDate(date)
                },
                date.year, date.monthValue - 1, date.dayOfMonth,
            ).show()
        }

        if (existing != null) {
            nameInput.setText(existing.name)
            categorySpinner.setSelection(categories.indexOf(existing.category).coerceAtLeast(0))
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.add_item else R.string.edit_item)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                Reminders.cancel(this, existing)
                saveItems(items.filter { it.id != existing.id })
            }
        }

        val dialog = builder.create()
        dialog.show()
        // Set here rather than in the builder so an empty name keeps the dialog open.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val name = nameInput.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                nameLayout.error = getString(R.string.name_required)
                return@setOnClickListener
            }
            val base = existing ?: Item(name = name, category = "", expiry = date)
            // Start at the current stage: no reminder for what the user has just typed in.
            val saved = base.copy(
                name = name,
                category = categorySpinner.selectedItem as String,
                expiry = date,
                notifiedStage = Reminders.stageFor(base.copy(expiry = date).daysLeft())
                    ?: Item.NOT_NOTIFIED,
            )
            if (existing != null) Reminders.cancel(this, existing)
            saveItems(items.filter { it.id != saved.id } + saved)
            dialog.dismiss()
        }
    }
}
