package com.example.reminderapp;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;

public class AddTaskActivity extends AppCompatActivity {

    private EditText etTitle, etDesc;
    private ImageView ivPreview;
    private String savedImagePath = "";
    private Calendar calendar = Calendar.getInstance();
    private int taskId = -1; // -1 = New Task
    private DatabaseHelper dbHelper;

    // 1. MODERN PHOTO PICKER (Gallery)
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMedia =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    processAndSetImage(uri);
                }
            });

    // 2. CAMERA LAUNCHER
    private final ActivityResultLauncher<Intent> takePhoto =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Bitmap bitmap = (Bitmap) result.getData().getExtras().get("data");
                    ivPreview.setImageBitmap(bitmap);
                    savedImagePath = saveToInternalStorage(bitmap);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_task);

        dbHelper = new DatabaseHelper(this);
        etTitle = findViewById(R.id.taskTitleEditText);
        etDesc = findViewById(R.id.taskDescriptionEditText);
        ivPreview = findViewById(R.id.taskImageView);

        // Click Listeners
        findViewById(R.id.btnPickDateTime).setOnClickListener(v -> showDateTimePicker());
        findViewById(R.id.btnSelectImage).setOnClickListener(v -> showImageOptions());
        findViewById(R.id.btnSaveTask).setOnClickListener(v -> validateAndSave());
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());

        // EDIT MODE CHECK: Did Person 1 send a Task ID?
        if (getIntent().hasExtra("taskId")) {
            taskId = getIntent().getIntExtra("taskId", -1);
            loadExistingTask(taskId);
        }
    }

    private void showImageOptions() {
        String[] options = {"Gallery (Modern)", "Camera"};
        new AlertDialog.Builder(this)
                .setTitle("Attach Photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        pickMedia.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build());
                    } else {
                        takePhoto.launch(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
                    }
                }).show();
    }

    private void processAndSetImage(Uri uri) {
        try {
            // Convert URI to Bitmap to save a local copy
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            ivPreview.setImageBitmap(bitmap);
            savedImagePath = saveToInternalStorage(bitmap);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }

    private String saveToInternalStorage(Bitmap bitmap) {
        File directory = getFilesDir();
        String fileName = "img_" + System.currentTimeMillis() + ".jpg";
        File file = new File(directory, fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            return file.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }

    private void showDateTimePicker() {
        new DatePickerDialog(this, (view, y, m, d) -> {
            calendar.set(Calendar.YEAR, y);
            calendar.set(Calendar.MONTH, m);
            calendar.set(Calendar.DAY_OF_MONTH, d);
            
            new TimePickerDialog(this, (v, h, min) -> {
                calendar.set(Calendar.HOUR_OF_DAY, h);
                calendar.set(Calendar.MINUTE, min);
                Toast.makeText(this, "Reminder set!", Toast.LENGTH_SHORT).show();
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadExistingTask(int id) {
        Task task = dbHelper.getTaskById(id);
        if (task != null) {
            etTitle.setText(task.getTitle());
            etDesc.setText(task.getDescription());
            savedImagePath = task.getImagePath();
            if (!savedImagePath.isEmpty()) {
                ivPreview.setImageBitmap(BitmapFactory.decodeFile(savedImagePath));
            }
            calendar.setTimeInMillis(task.getTimestamp());
        }
    }

    private void validateAndSave() {
        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            etTitle.setError("Title required");
            return;
        }

        Task task = new Task(title, etDesc.getText().toString(), calendar.getTimeInMillis(), savedImagePath);
        
        boolean success;
        if (taskId == -1) {
            success = dbHelper.addTask(task) > 0;
        } else {
            task.setId(taskId);
            success = dbHelper.updateTask(task);
        }

        if (success) {
            setResult(RESULT_OK);
            finish();
        }
    }
}
