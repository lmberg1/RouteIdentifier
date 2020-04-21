package com.example.routeidentifier;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.exifinterface.media.ExifInterface;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;

import com.ortiz.touchview.TouchImageView;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY;

public class MeasureBoltDistanceActivity extends AppCompatActivity {

    private static final String TAG = "BoltDistanceActivity";
    private static final int GALLERY_REQUEST_CODE = 1;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    // View that allows users to zoom in on an image
    private TouchImageView imageView;
    // Current image shown in the imageView
    private Bitmap imageBitmap;
    // Helper class to measure distance between bolts
    private BoltMeasurer boltMeasurer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measure_bolt_distance);

        // Try to get permission to access user photos
        int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    this,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }

        // Set up the imageView
        imageView = findViewById(R.id.image);
        imageView.setMaxZoom(40);

        // Get default bitmap
        Bitmap fullSized = BitmapFactory.decodeResource(getResources(), R.drawable.boltsample);
        imageView.setImageResource(R.drawable.boltsample);

        // Scale the image down
        Matrix matrix = new Matrix();
        int w = fullSized.getWidth();
        int h = fullSized.getHeight();
        float scale = (h > w) ? 4000f / h : 4000f / w;
        matrix.postScale(scale, scale);
        imageBitmap = Bitmap.createBitmap(fullSized, 0, 0, w, h, matrix, true);

        // Initialize bolt measurer helper class
        boltMeasurer = new BoltMeasurer();
        boltMeasurer.setImageBitmap(imageBitmap);

        // Activate button to let user choose another image
        Button button = findViewById(R.id.chooseImage);
        button.setOnClickListener(view -> pickFromGallery());

        // Activate the bolt selector button
        Button buttonPickBolt = findViewById(R.id.pickBolt);
        buttonPickBolt.setOnClickListener(view -> measureBolt());
    }

    public void measureBolt() {
        RectF zoom = imageView.getZoomedRect();
        imageBitmap = boltMeasurer.detectBolt(zoom);
        imageView.setImageBitmap(imageBitmap);
    }

    // Create intent to let user pick image from gallery
    public void pickFromGallery(){
        //Create an Intent with action as ACTION_PICK
        Intent intent = new Intent(Intent.ACTION_PICK);
        // Sets the type as image/*. This ensures only components of type image are selected
        intent.setType("image/*");
        //We pass an extra array with the accepted mime types. This will ensure only components with these MIME types as targeted.
        String[] mimeTypes = {"image/jpeg", "image/png"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        // Launching the Intent
        startActivityForResult(intent, GALLERY_REQUEST_CODE);
    }

    // Called when user selects an image from their gallery
    @Override
    public void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode, resultCode, data);

        // Result code is RESULT_OK only if the user selects an Image
        if (resultCode == Activity.RESULT_OK)
            switch (requestCode){
                case GALLERY_REQUEST_CODE:
                    //data.getData returns the content URI for the selected Image
                    Uri selectedImage = data.getData();
                    imageView.setImageURI(selectedImage);

                    String imgDecodableString = getImagePath(selectedImage);

                    // Decode image and its metadata
                    byte[] full_size = null;
                    String rotation = null;
                    try {
                        // Save metadata about image orientation
                        ExifInterface exif = new ExifInterface(imgDecodableString);
                        rotation = exif.getAttribute(ExifInterface.TAG_ORIENTATION);

                        // Read in the image into a byte array
                        InputStream iStream = getContentResolver().openInputStream(selectedImage);

                        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
                        int bufferSize = 1024;
                        byte[] buffer = new byte[bufferSize];

                        int len = 0;
                        while ((len = iStream.read(buffer)) != -1) {
                            byteBuffer.write(buffer, 0, len);
                        }
                        full_size = byteBuffer.toByteArray();
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }

                    // Convert the image byte array into a bitmap
                    Bitmap bitmap = BitmapFactory.decodeByteArray(full_size, 0, full_size.length);

                    // Create a matrix to rotate the image
                    Matrix matrix = new Matrix();
                    if (Integer.parseInt(rotation) == ExifInterface.ORIENTATION_ROTATE_90) {
                        matrix.postRotate(90);
                    }

                    // Rotate and save the bitmap
                    int w = bitmap.getWidth();
                    int h = bitmap.getHeight();
                    imageBitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
                    boltMeasurer.setImageBitmap(imageBitmap);
            }
    }

    private String getImagePath(Uri selectedImage) {
        String[] filePathColumn = { MediaStore.Images.Media.DATA };
        // Get the cursor
        Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
        // Move to first row
        cursor.moveToFirst();
        //Get the column index of MediaStore.Images.Media.DATA
        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        //Gets the String value in the column
        String imgDecodableString = cursor.getString(columnIndex);
        cursor.close();
        return imgDecodableString;
    }
}
