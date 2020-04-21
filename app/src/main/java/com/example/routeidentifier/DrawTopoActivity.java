package com.example.routeidentifier;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Path;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.exifinterface.media.ExifInterface;

import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteFindIterable;
import org.bson.BsonBinary;
import org.bson.Document;
import org.opencv.core.Point;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.example.routeidentifier.StartActivity.coll;
import static com.example.routeidentifier.StartActivity.image_coll;
import static com.example.routeidentifier.StartActivity.stitchUser;

public class DrawTopoActivity extends AppCompatActivity {
    private static final String TAG = "MongoActivity";
    private static final int GALLERY_REQUEST_CODE = 1;

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    // View to allow users to draw on an image
    private DrawingView drawingView;

    // Array to store image metadata
    private static String[] imgdata;
    // Stores image as byte array
    private static byte[] byte_arr;

    // Elements of the AlertDialog to save route information
    private EditText routeNameEditText = null;
    private EditText routeGradeEditText = null;
    private EditText routeBoltsEditText = null;
    private Button saveUserDataButton = null;
    private Button cancelUserDataButton = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_draw_topo);

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

        // Setup button to let user choose an image
        Button button = findViewById(R.id.chooseImage);
        button.setOnClickListener(view -> pickFromGallery());

        // Setup button to let user draw a route
        Button drawButton = findViewById(R.id.drawRoute);
        drawButton.setOnClickListener(view -> onDrawRouteClick(view));
        drawButton.setVisibility(View.INVISIBLE); // hide button until an image is chosen

        // Setup button to delete a route
        final Button deleteButton = findViewById(R.id.deleteButton);
        deleteButton.setOnClickListener(view -> onDeleteRouteClick(view));
        deleteButton.setVisibility(View.INVISIBLE);
        drawingView = findViewById(R.id.drawingView);
        drawingView.setDeleteButton(deleteButton);
    }

    // Let DrawingView know that drawing can begin
    public void onDrawRouteClick(View view) {
        drawingView.setReady(true);
    }

    // Let DrawingView know to delete whatever was drawn
    public void onDeleteRouteClick(View view) {
        drawingView.onDelete(view);
    }

    // Display popup for user to enter information about the route they drew
    public AlertDialog createAlertDialog() {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(DrawTopoActivity.this);
        // Set title, icon, can not cancel properties.
        alertDialogBuilder.setTitle("Route Data Collection Dialog.");
        alertDialogBuilder.setIcon(R.drawable.ic_launcher_background);
        alertDialogBuilder.setCancelable(false);

        // Get layout inflater object.
        LayoutInflater layoutInflater = LayoutInflater.from(DrawTopoActivity.this);

        // Inflate the popup dialog from a layout xml file.
        // Below edittext and button are all exist in the popup dialog view.
        View popupInputDialogView = layoutInflater.inflate(R.layout.popup_route_info, null);

        // Setup spinner to show state options
        Spinner spinner = popupInputDialogView.findViewById(R.id.listStates);
        RemoteFindIterable<Document> query = coll.find(Filters.regex("path", ",States,$"));
        ArrayList<Document> result = new ArrayList<>();
        query.into(result).addOnSuccessListener(documents -> {
            ArrayList<String> states = new ArrayList<>();
            for (Document d : documents) {
                states.add(d.getString("_id"));
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, states);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        });

        // Setup spinner to display region options
        Spinner spinner2 = popupInputDialogView.findViewById(R.id.listRegions);
        RemoteFindIterable<Document> query2 = coll.find(Filters.regex("path", ",States,Kentucky,$"));
        ArrayList<Document> result2 = new ArrayList<>();
        query2.into(result2).addOnSuccessListener(documents -> {
            ArrayList<String> regions = new ArrayList<>();
            Log.e("Activity", String.format("%d", documents.size()));
            for (Document d : documents) {
                regions.add(d.getString("_id"));
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, regions);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner2.setAdapter(adapter);
        });

        // Setup spinner to display area options
        Spinner spinner3 = popupInputDialogView.findViewById(R.id.listAreas);
        RemoteFindIterable<Document> query3 = coll.find(Filters.regex("path", ",States,Kentucky,Red River Gorge,$"));
        ArrayList<Document> result3 = new ArrayList<>();
        query3.into(result3).addOnSuccessListener(documents -> {
            ArrayList<String> regions = new ArrayList<>();
            Log.e("Activity", String.format("%d", documents.size()));
            for (Document d : documents) {
                regions.add(d.getString("_id"));
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, regions);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner3.setAdapter(adapter);
        });

        // Get user input edittext and button ui controls in the popup dialog.
        routeNameEditText = popupInputDialogView.findViewById(R.id.routeName);
        routeGradeEditText = popupInputDialogView.findViewById(R.id.routeGrade);
        routeBoltsEditText = popupInputDialogView.findViewById(R.id.routeBolts);
        saveUserDataButton = popupInputDialogView.findViewById(R.id.button_save_user_data);
        cancelUserDataButton = popupInputDialogView.findViewById(R.id.button_cancel_user_data);

        // Set the inflated layout view object to the AlertDialog builder.
        alertDialogBuilder.setView(popupInputDialogView);

        // Create AlertDialog and show.
        final AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
        return alertDialog;
    }

    // Display popup to add route information once drawing is finished
    public class DrawCallback implements DrawingView.DrawCallback {
        @Override
        public void drawCallback(final Path path) {
            // Create a AlertDialog Builder.
            AlertDialog alertDialog = createAlertDialog();

            // Let user cancel dialog
            cancelUserDataButton.setOnClickListener(view -> {
                alertDialog.cancel();
            });

            // When user clicks the save user data button in the popup dialog.
            saveUserDataButton.setOnClickListener(view -> {

                // Get user data from popup dialog editext.
                String routeName = routeNameEditText.getText().toString();
                String routeGrade = routeGradeEditText.getText().toString();
                String routeBolts = routeBoltsEditText.getText().toString();

                // Create data for the listview.
                String[] dataArr = {routeName, routeGrade, routeBolts};

                // Display name of route added to database
                Toast t = Toast.makeText(DrawTopoActivity.this, dataArr[0], Toast.LENGTH_SHORT);
                t.show();

                // Create new data document to add to database
                Document doc = new Document();
                doc.append("user_id", stitchUser.getId());
                doc.append("_id", dataArr[0]);
                doc.append("grade", dataArr[1]);
                doc.append("bolts", dataArr[2]);
                doc.append("path", ",States,Kentucky,Red River Gorge,Roadside,");

                // Create new image document to add to database
                Document image_doc = new Document();
                image_doc.append("user_id", stitchUser.getId());
                image_doc.append("name", dataArr[0]);
                image_doc.append("lat", imgdata[0]);
                image_doc.append("lon", imgdata[1]);
                image_doc.append("orient", imgdata[2]);

                // Add the points of the user-drawn path to image document
                List<BasicDBObject> pointArray = new ArrayList<>();
                ArrayList<Point> points = drawingView.points.get(drawingView.selectedIndex);
                for (Point p : points) {
                    BasicDBObject o = new BasicDBObject();
                    o.put("x", (int) p.x);
                    o.put("y", (int) p.y);
                    pointArray.add(o);
                }
                image_doc.append("points", pointArray);

                // Add image to the image document
                BsonBinary b = new BsonBinary(byte_arr);
                image_doc.append("image", b);

                // Try to add the documents to the database
                coll.insertOne(doc).addOnSuccessListener(
                        document -> Log.e(TAG, "Route info document inserted")
                );
                image_coll.insertOne(image_doc).addOnSuccessListener(remoteInsertOneResult -> {
                    Log.e(TAG, "Image data document inserted");

                });
                alertDialog.cancel();
            });
        }
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
                    String imgDecodableString = getImagePath(selectedImage);

                    // Initialize arrays to hold information about image
                    byte[] full_size = null;
                    byte_arr = null;
                    imgdata = new String[3];

                    // Decode image and its metadata
                    ExifInterface exif = null;
                    try {
                        // Save image metadata for location and orientation
                        exif = new ExifInterface(imgDecodableString);
                        imgdata[0] = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
                        imgdata[1] = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
                        imgdata[2] = exif.getAttribute(ExifInterface.TAG_ORIENTATION);

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
                    Bitmap rotated = null;

                    // Scale the image down
                    Matrix matrix = new Matrix();
                    int w = bitmap.getWidth();
                    int h = bitmap.getHeight();
                    float scale = (h > w) ? 750f / h : 750f / w;
                    matrix.postScale(scale, scale);

                    // Rotate the image if necessary
                    if (Integer.parseInt(imgdata[2]) == ExifInterface.ORIENTATION_ROTATE_90) {
                        matrix.postRotate(90);
                    }
                    rotated = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);

                    // Convert the rotated bitmap back into a byte array
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    rotated.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    byte_arr = stream.toByteArray();

                    // Put the image into the ImageView
                    ImageView imageView = findViewById(R.id.imageView);
                    imageView.setImageBitmap(rotated);

                    // Figure out the true size of the image in the ImageView
                    int im_width = rotated.getWidth();
                    int im_height = rotated.getHeight();
                    int vw_width = imageView.getMeasuredWidth();

                    // Change the ImageView and DrawView params to match the image size
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) imageView.getLayoutParams();
                    FrameLayout.LayoutParams drawParams = (FrameLayout.LayoutParams) drawingView.getLayoutParams();
                    params.width = vw_width;
                    params.height = im_height * vw_width / im_width;
                    drawParams.width = vw_width;
                    drawParams.height = im_height * vw_width / im_width;
                    imageView.setLayoutParams(params);
                    drawingView.setLayoutParams(drawParams);

                    // Reset the canvas
                    //drawingView.resetDrawingView();

                    // Show button to let user start drawing start drawing
                    Button drawButton = findViewById(R.id.drawRoute);
                    drawButton.setVisibility(View.VISIBLE);

                    drawingView.setDrawCallback(new DrawCallback());
                    break;
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

