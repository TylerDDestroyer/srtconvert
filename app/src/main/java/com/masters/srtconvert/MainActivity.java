package com.masters.srtconvert;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_FILE_REQUEST_CODE = 1;
    private static final int CREATE_FILE_REQUEST_CODE = 2;
    private Uri assFileUri;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnSelectFile = findViewById(R.id.btnSelectFile);
        tvStatus = findViewById(R.id.tvStatus);

        btnSelectFile.setOnClickListener(v -> checkPermissionsAndOpenFilePicker());
    }

    private void checkPermissionsAndOpenFilePicker() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PICK_FILE_REQUEST_CODE);
        } else {
            openFilePicker();
        }
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_FILE_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            assFileUri = data.getData();
            if (assFileUri != null) {
                String fileName = getFileName(assFileUri);
                if (fileName != null) {
                    String srtFileName = fileName.replace(".ass", ".srt");
                    createSrtFile(srtFileName);
                }
            }
        } else if (requestCode == CREATE_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            Uri srtFileUri = data.getData();
            if (srtFileUri != null) {
                try {
                    convertAssToSrt(assFileUri, srtFileUri);
                    tvStatus.setText("Conversion complete: " + srtFileUri.getPath());
                } catch (IOException e) {
                    tvStatus.setText("Error during conversion: " + e.getMessage());
                }
            }
        }
    }

    private void createSrtFile(String srtFileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/srt");
        intent.putExtra(Intent.EXTRA_TITLE, srtFileName);
        startActivityForResult(intent, CREATE_FILE_REQUEST_CODE);
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void convertAssToSrt(Uri assFileUri, Uri srtFileUri) throws IOException {
        try (InputStream inputStream = getContentResolver().openInputStream(assFileUri);
             OutputStream outputStream = getContentResolver().openOutputStream(srtFileUri)) {

            if (inputStream != null && outputStream != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder srtContent = new StringBuilder();
                String line;
                int index = 1;
                boolean inEventSection = false;

                while ((line = reader.readLine()) != null) {
                    if (line.contains("[Events]")) {
                        inEventSection = true;
                        continue;
                    }
                    if (inEventSection && line.startsWith("Dialogue:")) {
                        String[] parts = line.split(",", 10);
                        String startTime = parts[1].replace('.', ',');
                        String endTime = parts[2].replace('.', ',');
                        String text = parts[9].replaceAll("\\{.*?\\}", "").replace("\\N", "\n").trim();

                        srtContent.append(index).append("\n");
                        srtContent.append(startTime).append(" --> ").append(endTime).append("\n");
                        srtContent.append(text).append("\n\n");

                        index++;
                    }
                }

                outputStream.write(srtContent.toString().getBytes());
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PICK_FILE_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openFilePicker();
        }
    }
}
