package com.example.expensetracker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ExpenseTracker.db";
    private static final int DATABASE_VERSION = 4;

    public static final String TABLE_EXPENSES = "expenses";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_AMOUNT = "amount";
    public static final String COLUMN_CATEGORY = "category";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_PERSON_ID = "person_id";

    public static final String TABLE_PEOPLE = "people";
    public static final String COLUMN_PERSON_ID_PK = "id";
    public static final String COLUMN_PERSON_NAME = "name";
    public static final String COLUMN_PERSON_PHONE = "phone";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_PEOPLE_TABLE = "CREATE TABLE " + TABLE_PEOPLE + "("
                + COLUMN_PERSON_ID_PK + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_PERSON_NAME + " TEXT,"
                + COLUMN_PERSON_PHONE + " TEXT" + ")";
        db.execSQL(CREATE_PEOPLE_TABLE);

        String CREATE_EXPENSES_TABLE = "CREATE TABLE " + TABLE_EXPENSES + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_TITLE + " TEXT,"
                + COLUMN_AMOUNT + " REAL,"
                + COLUMN_CATEGORY + " TEXT,"
                + COLUMN_DATE + " INTEGER,"
                + COLUMN_PERSON_ID + " INTEGER,"
                + "FOREIGN KEY(" + COLUMN_PERSON_ID + ") REFERENCES " + TABLE_PEOPLE + "(" + COLUMN_PERSON_ID_PK + ")" + ")";
        db.execSQL(CREATE_EXPENSES_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 4) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_EXPENSES);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PEOPLE);
            db.execSQL("DROP TABLE IF EXISTS customers");
            onCreate(db);
        }
    }

    public long addPerson(Person person) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PERSON_NAME, person.getName());
        values.put(COLUMN_PERSON_PHONE, person.getPhone());
        long id = db.insert(TABLE_PEOPLE, null, values);
        db.close();
        return id;
    }

    public Person getPersonByName(String name) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PEOPLE, null, COLUMN_PERSON_NAME + " = ?", new String[]{name}, null, null, null);
        Person person = null;
        if (cursor.moveToFirst()) {
            person = new Person(cursor.getString(1), cursor.getString(2));
            person.setId(cursor.getInt(0));
        }
        cursor.close();
        db.close();
        return person;
    }

    public List<Person> getAllPeople() {
        List<Person> peopleList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_PEOPLE;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);
        if (cursor.moveToFirst()) {
            do {
                Person person = new Person(cursor.getString(1), cursor.getString(2));
                person.setId(cursor.getInt(0));
                peopleList.add(person);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return peopleList;
    }

    public void addExpense(Expense expense) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TITLE, expense.getTitle());
        values.put(COLUMN_AMOUNT, expense.getAmount());
        values.put(COLUMN_CATEGORY, expense.getCategory());
        values.put(COLUMN_DATE, expense.getDate());
        values.put(COLUMN_PERSON_ID, expense.getCustomerId());
        db.insert(TABLE_EXPENSES, null, values);
        db.close();
    }

    public List<Expense> getExpensesByMonth(int month, int year, int personId) {
        List<Expense> expenseList = new ArrayList<>();
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(year, month, 1, 0, 0, 0);
        long startOfMonth = calendar.getTimeInMillis();
        calendar.add(java.util.Calendar.MONTH, 1);
        long endOfMonth = calendar.getTimeInMillis();

        String selectQuery = "SELECT * FROM " + TABLE_EXPENSES + " WHERE " + COLUMN_DATE + " >= " + startOfMonth 
                + " AND " + COLUMN_DATE + " < " + endOfMonth;
        
        if (personId != -1) {
            selectQuery += " AND " + COLUMN_PERSON_ID + " = " + personId;
        }
        
        selectQuery += " ORDER BY " + COLUMN_DATE + " DESC";

        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                Expense expense = new Expense(
                        cursor.getString(1),
                        cursor.getDouble(2),
                        cursor.getString(3),
                        cursor.getLong(4)
                );
                expense.setId(cursor.getInt(0));
                expense.setCustomerId(cursor.getInt(5));
                expenseList.add(expense);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return expenseList;
    }

    public void deleteExpense(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_EXPENSES, COLUMN_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
    }

    public double getTotalByMonth(int month, int year, int personId) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(year, month, 1, 0, 0, 0);
        long startOfMonth = calendar.getTimeInMillis();
        calendar.add(java.util.Calendar.MONTH, 1);
        long endOfMonth = calendar.getTimeInMillis();

        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT SUM(" + COLUMN_AMOUNT + ") FROM " + TABLE_EXPENSES + " WHERE " + COLUMN_DATE + " >= ? AND " + COLUMN_DATE + " < ?";
        if (personId != -1) {
            query += " AND " + COLUMN_PERSON_ID + " = " + personId;
        }
        
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(startOfMonth), String.valueOf(endOfMonth)});
        double total = 0;
        if (cursor.moveToFirst()) {
            total = cursor.getDouble(0);
        }
        cursor.close();
        db.close();
        return total;
    }
}
