package com.example.routeidentifier;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import com.mongodb.client.model.Filters;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteFindIterable;
import com.otaliastudios.cameraview.CameraException;
import com.otaliastudios.cameraview.CameraListener;
import com.otaliastudios.cameraview.CameraOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.PictureResult;
import com.otaliastudios.cameraview.Gesture;
import com.otaliastudios.cameraview.GestureAction;
import android.widget.Spinner;

import org.bson.Document;
import org.opencv.android.OpenCVLoader;

import java.util.ArrayList;
import java.util.HashMap;

import static com.example.routeidentifier.StartActivity.coll;


public class CameraActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "MainActivity";
    protected CameraView camera;
    private long mCaptureTime;

    private HashMap<String, Document> routes;

    private void setupSpinner(Spinner spinner) {
        String path = ",States,Kentucky,Red River Gorge,Roadside,";
        RemoteFindIterable<Document> query = coll.find(Filters.regex("path", path));
        ArrayList<Document> result = new ArrayList<>();
        query.into(result).addOnSuccessListener(documents -> {
            Log.e(TAG, "Success");
            ArrayList<String> states = new ArrayList<>();
            for (Document d : documents) {
                String name = d.getString("_id");
                states.add(name);
                routes.put(name, d);
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(CameraActivity.this, android.R.layout.simple_spinner_dropdown_item, states);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setOnItemSelectedListener(new DropdownListener());
        }).addOnCanceledListener(() -> Log.e(TAG, "Canceled"))
                .addOnFailureListener(documents -> Log.e(TAG, "Failed"));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        routes = new HashMap<>();
        Spinner spinner = findViewById(R.id.spinner);
        setupSpinner(spinner);


        if (!OpenCVLoader.initDebug()) {
            Log.e(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), not working.");
        } else {
            Log.i(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), working.");
        }

        camera = findViewById(R.id.camera);
        camera.setLifecycleOwner(this);
        camera.addCameraListener(new Listener());
        camera.mapGesture(Gesture.PINCH, GestureAction.ZOOM); // Pinch to zoom!
        camera.mapGesture(Gesture.TAP, GestureAction.FOCUS_WITH_MARKER); // Tap to focus!

        findViewById(R.id.snapPicture).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            //
            case R.id.snapPicture:
                capturePictureSnapshot();
                break;
        }
    }

    private void capturePictureSnapshot() {
        if (camera.isTakingPicture()) return;
        mCaptureTime = System.currentTimeMillis();
        Log.e(TAG, "Capturing picture snapshot...");
        camera.takePictureSnapshot();
    }

    private class Listener extends CameraListener {

        @Override
        public void onCameraOpened(@NonNull CameraOptions options) {
            //camera.addFrameProcessor(new InnerFrameProcessor(MainActivity.this));
        }

        @Override
        public void onCameraError(@NonNull CameraException exception) {
            super.onCameraError(exception);
            Log.e(TAG, "Got CameraException #" + exception.getReason());
        }

        @Override
        public void onPictureTaken(@NonNull PictureResult result) {
            super.onPictureTaken(result);

            // This can happen if picture was taken with a gesture.
            long callbackTime = System.currentTimeMillis();
            if (mCaptureTime == 0) mCaptureTime = callbackTime - 300;
            Log.e(TAG, "onPictureTaken called! Launching activity. Delay:");
            PicturePreviewActivity.setPictureResult(result);
            Intent intent = new Intent(CameraActivity.this, PicturePreviewActivity.class);
            intent.putExtra("delay", callbackTime - mCaptureTime);
            startActivity(intent);
            mCaptureTime = 0;
            Log.e(TAG, "onPictureTaken called! Launched activity.");
        }
    }

    private static class DropdownListener implements AdapterView.OnItemSelectedListener {
        public void onItemSelected(AdapterView<?> parent, View view,
                                   int pos, long id) {
            // An item was selected. You can retrieve the selected item using
            String selected_photo = parent.getItemAtPosition(pos).toString();
            PicturePreviewActivity.setTargetPhoto(selected_photo);
        }

        public void onNothingSelected(AdapterView<?> parent) {
            // Another interface callback
        }
    }
}