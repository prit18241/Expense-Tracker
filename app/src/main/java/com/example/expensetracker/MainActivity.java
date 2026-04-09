package com.example.expensetracker;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ExpenseAdapter adapter;
    private DatabaseHelper db;
    private FloatingActionButton fabAdd;
    private TextView tvTotalAmount, tvMonthDisplay, tvLimitInfo, tvCurrentAccount;
    private ProgressBar limitProgress;
    private Calendar currentCalendar;
    private double monthlyLimit = 0;
    private SharedPreferences prefs;
    private BarChart mainBarChart;
    private int selectedPersonId = -1; // -1 means Main/Admin account

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new DatabaseHelper(this);
        prefs = getSharedPreferences("ExpensePrefs", MODE_PRIVATE);
        monthlyLimit = prefs.getFloat("limit", 0);
        selectedPersonId = prefs.getInt("last_person_id", -1);

        recyclerView = findViewById(R.id.recyclerView);
        fabAdd = findViewById(R.id.fabAdd);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        tvMonthDisplay = findViewById(R.id.tvMonthDisplay);
        tvLimitInfo = findViewById(R.id.tvLimitInfo);
        limitProgress = findViewById(R.id.limitProgress);
        mainBarChart = findViewById(R.id.mainBarChart);
        tvCurrentAccount = findViewById(R.id.tvCurrentAccount);

        currentCalendar = Calendar.getInstance();

        adapter = new ExpenseAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        adapter.setOnItemLongClickListener(this::showDeleteDialog);

        findViewById(R.id.btnPrevMonth).setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            updateUI();
        });

        findViewById(R.id.btnNextMonth).setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            updateUI();
        });

        findViewById(R.id.btnSetLimit).setOnClickListener(v -> showSetLimitDialog());
        
        ImageButton btnSwitchAccount = findViewById(R.id.btnSwitchAccount);
        if (btnSwitchAccount != null) {
            btnSwitchAccount.setOnClickListener(v -> showSwitchAccountDialog());
        }

        setupBarChart();
        updateUI();

        fabAdd.setOnClickListener(v -> showAddExpenseDialog());
    }

    private void setupBarChart() {
        mainBarChart.getDescription().setEnabled(false);
        mainBarChart.setDrawGridBackground(false);
        mainBarChart.getLegend().setEnabled(true);
        mainBarChart.getLegend().setTextColor(Color.WHITE);
        
        XAxis xAxis = mainBarChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setValueFormatter(new ValueFormatter() {
            private final String[] labels = new String[]{"Spent", "Income"};
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < labels.length) {
                    return labels[index];
                }
                return "";
            }
        });

        YAxis leftAxis = mainBarChart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.LTGRAY);

        YAxis rightAxis = mainBarChart.getAxisRight();
        rightAxis.setTextColor(Color.WHITE);
        rightAxis.setDrawGridLines(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_yearly_history) {
            showYearlyHistory();
            return true;
        } else if (id == R.id.action_manage_people) {
            showSwitchAccountDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSwitchAccountDialog() {
        List<Person> people = db.getAllPeople();
        String[] accountNames = new String[people.size() + 2];
        accountNames[0] = "Main Account (Default)";
        accountNames[1] = "+ Add New User";
        for (int i = 0; i < people.size(); i++) {
            accountNames[i + 2] = "User: " + people.get(i).getName();
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Account")
                .setItems(accountNames, (dialog, which) -> {
                    if (which == 0) {
                        selectedPersonId = -1;
                        saveSelectedAccount();
                        updateUI();
                    } else if (which == 1) {
                        showAddPersonDialog();
                    } else {
                        selectedPersonId = people.get(which - 2).getId();
                        saveSelectedAccount();
                        updateUI();
                    }
                })
                .show();
    }

    private void saveSelectedAccount() {
        prefs.edit().putInt("last_person_id", selectedPersonId).apply();
    }

    private void showAddPersonDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_add_person, null);
        EditText etName = view.findViewById(R.id.etPersonName);
        EditText etPhone = view.findViewById(R.id.etPersonPhone);

        new AlertDialog.Builder(this)
                .setTitle("Add New User")
                .setView(view)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = etName.getText().toString();
                    String phone = etPhone.getText().toString();
                    if (!name.isEmpty()) {
                        long id = db.addPerson(new Person(name, phone));
                        selectedPersonId = (int) id;
                        saveSelectedAccount();
                        updateUI();
                        Toast.makeText(this, "New user added: " + name, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showYearlyHistory() {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        StringBuilder history = new StringBuilder();
        history.append("Yearly Summary for ").append(currentYear).append(":\n\n");
        
        double yearlyTotal = 0;
        for (int i = 0; i < 12; i++) {
            double monthTotal = db.getTotalByMonth(i, currentYear, selectedPersonId);
            if (monthTotal != 0) {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.MONTH, i);
                String monthName = new SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.getTime());
                history.append(monthName).append(": ").append(String.format(Locale.getDefault(), "$%.2f", monthTotal)).append("\n");
                yearlyTotal += monthTotal;
            }
        }
        history.append("\nTotal Yearly Balance: ").append(String.format(Locale.getDefault(), "$%.2f", yearlyTotal));

        new AlertDialog.Builder(this)
                .setTitle("Yearly History")
                .setMessage(history.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    private void updateUI() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonthDisplay.setText(sdf.format(currentCalendar.getTime()));
        
        String accountName = "Main Account";
        if (selectedPersonId != -1) {
            List<Person> people = db.getAllPeople();
            for (Person p : people) {
                if (p.getId() == selectedPersonId) {
                    accountName = p.getName() + "'s Account";
                    break;
                }
            }
        }
        tvCurrentAccount.setText("(" + accountName + ")");

        int month = currentCalendar.get(Calendar.MONTH);
        int year = currentCalendar.get(Calendar.YEAR);

        List<Expense> expenses = db.getExpensesByMonth(month, year, selectedPersonId);
        adapter.setExpenses(expenses);

        double totalSpent = 0;
        double totalIncome = 0;
        for (Expense e : expenses) {
            if (e.getAmount() < 0) {
                totalSpent += Math.abs(e.getAmount());
            } else {
                totalIncome += e.getAmount();
            }
        }

        double balance = totalIncome - totalSpent;
        tvTotalAmount.setText(String.format(Locale.getDefault(), "$%.2f", balance));
        
        if (balance < 0) {
            tvTotalAmount.setTextColor(Color.RED);
        } else {
            tvTotalAmount.setTextColor(Color.WHITE);
        }

        updateBarChart(totalSpent, totalIncome);
        
        if (monthlyLimit > 0) {
            tvLimitInfo.setVisibility(View.VISIBLE);
            limitProgress.setVisibility(View.VISIBLE);
            tvLimitInfo.setText(String.format(Locale.getDefault(), "Limit: $%.2f", monthlyLimit));
            int progress = (int) ((totalSpent / monthlyLimit) * 100);
            limitProgress.setProgress(Math.min(progress, 100));
            
            if (totalSpent > monthlyLimit) {
                Toast.makeText(this, "Alert: Expense limit exceeded!", Toast.LENGTH_SHORT).show();
            }
        } else {
            tvLimitInfo.setVisibility(View.GONE);
            limitProgress.setVisibility(View.GONE);
        }
    }

    private void updateBarChart(double spent, double income) {
        ArrayList<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, (float) spent));
        entries.add(new BarEntry(1f, (float) income));

        BarDataSet dataSet = new BarDataSet(entries, "Spending vs Income");
        dataSet.setColors(new int[]{Color.RED, Color.parseColor("#4CAF50")});
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(12f);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.5f);
        
        mainBarChart.setData(barData);
        mainBarChart.animateY(1000);
        mainBarChart.invalidate();
    }

    private void showSetLimitDialog() {
        EditText etLimit = new EditText(this);
        etLimit.setHint("Enter monthly limit");
        etLimit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etLimit.setText(String.valueOf(monthlyLimit));

        new AlertDialog.Builder(this)
                .setTitle("Set Monthly Limit")
                .setView(etLimit)
                .setPositiveButton("Save", (dialog, which) -> {
                    String val = etLimit.getText().toString();
                    if (!val.isEmpty()) {
                        monthlyLimit = Double.parseDouble(val);
                        prefs.edit().putFloat("limit", (float) monthlyLimit).apply();
                        updateUI();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteDialog(Expense expense) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Record")
                .setMessage("Are you sure you want to delete this record?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    db.deleteExpense(expense.getId());
                    updateUI();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddExpenseDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_expense, null);
        builder.setView(dialogView);

        EditText etTitle = dialogView.findViewById(R.id.etTitle);
        EditText etAmount = dialogView.findViewById(R.id.etAmount);
        EditText etCategory = dialogView.findViewById(R.id.etCategory);
        
        // Hide the "Select Person" UI from the Add Expense dialog as we handle it by switching accounts
        View spinnerCustomer = dialogView.findViewById(R.id.spinnerCustomer);
        if (spinnerCustomer != null) {
            spinnerCustomer.setVisibility(View.GONE);
        }

        // Also hide the "Select Customer (Optional)" text if it exists
        View tvSelectCustomer = dialogView.findViewById(R.id.tvSelectCustomer);
        if (tvSelectCustomer != null) {
            tvSelectCustomer.setVisibility(View.GONE);
        }

        builder.setTitle("Add Income/Expense")
                .setPositiveButton("Add", (dialog, which) -> {
                    String title = etTitle.getText().toString();
                    String amountStr = etAmount.getText().toString();
                    String category = etCategory.getText().toString();

                    if (!title.isEmpty() && !amountStr.isEmpty()) {
                        try {
                            double amount = Double.parseDouble(amountStr);
                            Expense expense = new Expense(title, amount, category, System.currentTimeMillis());
                            // Automatically assign to the currently logged-in user
                            expense.setCustomerId(selectedPersonId);
                            db.addExpense(expense);
                            
                            updateUI();
                            
                        } catch (NumberFormatException e) {
                            Toast.makeText(MainActivity.this, "Invalid amount", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null);

        builder.create().show();
    }
}
