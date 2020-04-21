package com.example.routeidentifier;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.mongodb.client.model.Filters;
import com.mongodb.stitch.android.services.mongodb.remote.RemoteFindIterable;
import com.ortiz.touchview.TouchImageView;
import com.otaliastudios.cameraview.AspectRatio;
import com.otaliastudios.cameraview.BitmapCallback;
import com.otaliastudios.cameraview.PictureResult;

import org.bson.Document;
import org.bson.types.Binary;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;

import static com.example.routeidentifier.StartActivity.coll;
import static com.example.routeidentifier.StartActivity.image_coll;

public class PicturePreviewActivity extends AppCompatActivity {
    private static final String TAG = "PicturePreviewActivity";
    private static String route_name;
    private static String route_grade;
    private static String route_bolts;

    private static PictureResult picture;
    private static Document image_doc;
    private static Bitmap canvasBitmap;

    public static void setPictureResult(@Nullable PictureResult pictureResult) {
        picture = pictureResult;
    }

    public static void setTargetPhoto(String photo_name) {
        route_name = photo_name;

        // Search for the target photo in the image database
        RemoteFindIterable<Document> query = image_coll.find(Filters.regex("name", route_name)).limit(1);
        ArrayList<Document> image_result = new ArrayList<>();
        query.into(image_result).addOnSuccessListener(documents -> {
            // There should only be one result
            for (Document d : documents) {
                image_doc = d;
            }
        });

        // Search for the target route information in the information database
        RemoteFindIterable<Document> route_info_query = coll.find(Filters.regex("_id", route_name)).limit(1);
        ArrayList<Document> route_info_result = new ArrayList<>();
        route_info_query.into(route_info_result).addOnSuccessListener(documents -> {
            // There should only be one result
            for (Document d : documents) {
                route_grade = d.getString("grade");
                route_bolts = d.getString("bolts");
            }
        }).addOnCanceledListener(() -> Log.e(TAG, "Canceled"))
                .addOnFailureListener(documents -> Log.e(TAG, "Route Info Search Failed"));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture_preview);

        // Don't load page if picture is null
        final PictureResult result = picture;
        if (result == null) {
            finish();
            return;
        }

        // Wait until ImageView is expanded to setup the page
        final ImageView imageView = findViewById(R.id.image);
        ViewTreeObserver vto = imageView.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                imageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                setupPage();
            }
        });
    }

    private void setupPage() {
        // Get the picture and views
        final PictureResult result = picture;
        final TouchImageView imageView = findViewById(R.id.image);
        final ImageView overlayView = findViewById(R.id.container);

        // Read image from the database as a bitmap
        Binary b = (Binary) image_doc.get("image");
        byte[] byteArray = b.getData();
        Bitmap src = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);

        // Convert bitmap image to Mat
        final Mat srcMat = new Mat(src.getWidth(), src.getHeight(), CvType.CV_8UC4);
        Utils.bitmapToMat(src, srcMat);

        // Get the true size of the bitmap on the screen
        int src_width = imageView.getMeasuredWidth();
        int src_height = src.getHeight() * src_width / src.getWidth();

        // Create a bitmap to draw the route on
        canvasBitmap = Bitmap.createBitmap(src_width, src_height, Bitmap.Config.ARGB_8888);

        // Get the route lines and convert them into a Mat
        Bitmap bitmap_lines = getBitmapRouteLines();
        bitmap_lines = Bitmap.createScaledBitmap(bitmap_lines, src.getWidth(), src.getHeight(), false);
        final Mat lines = new Mat(bitmap_lines.getWidth(), bitmap_lines.getHeight(), CvType.CV_8UC4);
        Utils.bitmapToMat(bitmap_lines, lines);

        // Initialize the feature detector
        final FeatureDetector myFeatureDetector = new FeatureDetector();

        final long delay = getIntent().getLongExtra("delay", 0);
        AspectRatio ratio = AspectRatio.of(result.getSize());
        result.toBitmap(1000, 1000, bitmap -> {
            int w = bitmap.getWidth(), h = bitmap.getHeight();

            // Try to estimate the homography matrix between the source image and camera image
            Mat H = myFeatureDetector.getHomography(src, bitmap);
            if (H == null) { return; }

            // Warp src lines onto camera image
            Mat warp = new Mat();
            Imgproc.warpPerspective(lines, warp, H, new Size(w, h));

            // Convert warped lines to bitmap
            Bitmap line_overlay = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(warp, line_overlay);

            // Rotate the image and warped lines if phone orientation is horizontal
            if (w > h) {
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, matrix, true);
                line_overlay = Bitmap.createBitmap(line_overlay, 0, 0, w, h, matrix, true);
            }

            // Draw the image and the warped lines
            imageView.setImageBitmap(bitmap);
            overlayView.setImageBitmap(line_overlay);
            imageView.setMaxZoom(10);

            // Display the route information
            TextView textView = findViewById(R.id.routeLabel);
            textView.setText(String.format("%s (%s), %s bolts", route_name, route_grade, route_bolts));
        });
    }

    // Draw the route lines from the database onto the canvas bitmap
    private Bitmap getBitmapRouteLines() {
        Canvas drawCanvas = new Canvas(canvasBitmap);
        Path drawPath = new Path();

        // Paint to draw the route lines
        Paint drawPaint = new Paint();
        drawPaint.setColor(0xFF0033aa);
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(20);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);

        // Draw the route path from the points array
        ArrayList points = (ArrayList) image_doc.get("points");
        Document p0 = (Document) points.get(0);
        drawPath.moveTo(p0.getInteger("x"), p0.getInteger("y"));
        for (Object o : points) {
            Document point =  (Document) o;
            drawPath.lineTo(point.getInteger("x"), point.getInteger("y"));
        }
        drawCanvas.drawPath(drawPath, drawPaint);

        return canvasBitmap;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isChangingConfigurations()) {
            setPictureResult(null);
        }
    }
}
