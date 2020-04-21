package com.example.routeidentifier;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.widget.Button;

import org.opencv.core.Point;

import java.util.ArrayList;

public class DrawingView extends View {
    //drawing path
    private Path drawPath;
    //drawing and canvas paint
    private Paint drawPaint, highlightPaint, canvasPaint;
    //initial color
    private int paintColor = 0xFF0033aa;
    private int highlightColor = 0xfc0cb0;
    //canvas
    private Canvas drawCanvas;
    //canvas bitmap
    private Bitmap canvasBitmap;
    private boolean isDrawing;

    // Function to call once drawing is done
    private DrawCallback drawCallback;

    // Store the current routes drawn
    public ArrayList<Path> paths;
    public ArrayList<ArrayList<Point>> points;
    public int selectedIndex;
    private Button deleteButton;

    public DrawingView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setupDrawing();
        paths = new ArrayList<>();
        points = new ArrayList<>();
        selectedIndex = -1;
    }

    // Toggle drawing
    public void setReady(boolean isDrawing) {
        this.isDrawing = isDrawing;
    }

    // Set function to call once drawing finishes
    public void setDrawCallback(DrawCallback callback) {
        this.drawCallback = callback;
    }

    // Set delete button used to delete a path
    public void setDeleteButton(Button deleteButton) {
        this.deleteButton = deleteButton;
    }

    // Reset the canvas (called when a new image is chosen)
    public void resetDrawingView() {
        drawCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        paths = new ArrayList<>();
        points = new ArrayList<>();
        selectedIndex = -1;
    }

    // Called when user clicks delete button
    public void onDelete(View v) {
        if (selectedIndex == -1) return;

        // Remove currently selected path
        paths.remove(paths.get(selectedIndex));
        selectedIndex = -1;

        // Redraw paths
        drawCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        for (Path p : paths) { drawCanvas.drawPath(p, drawPaint); }

        // No path selected so can't delete any of the paths
        deleteButton.setVisibility(INVISIBLE);

        invalidate();
    }

    // Toggle highlight of a path a user has drawn
    public void highlightPath(Path path) {
        int i = paths.indexOf(path);

        // Unhighlight the path if it's already highlighted
        if (selectedIndex == i) {
            selectedIndex = -1;

            // Redraw paths
            drawCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            for (Path p : paths) { drawCanvas.drawPath(p, drawPaint); }

            // No path selected so can't delete any of the paths
            deleteButton.setVisibility(INVISIBLE);
        }
        // Highlight the path
        else {
            selectedIndex = i;

            // Redraw paths
            drawCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            for (Path p : paths) { drawCanvas.drawPath(p, drawPaint); }
            drawCanvas.drawPath(paths.get(i), highlightPaint);

            // Give user option to delete this path
            deleteButton.setVisibility(VISIBLE);
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        //view given size
        canvasBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        drawCanvas = new Canvas(canvasBitmap);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //draw view
        canvas.drawBitmap(canvasBitmap, 0, 0, canvasPaint);
        canvas.drawPath(drawPath, drawPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //detect user touch
        float touchX = event.getX();
        float touchY = event.getY();

        // Allow user to highlight routes that they have drawn
        if (!isDrawing) {
            if (event.getAction() != MotionEvent.ACTION_DOWN) { return true; }

            for (Path p : paths) {
                Path touchRegion = new Path();
                touchRegion.moveTo(touchX, touchY);
                touchRegion.addCircle(touchX, touchY, 5, Path.Direction.CW);

                touchRegion.op(p, Path.Op.DIFFERENCE);
                if (touchRegion.isEmpty()) {
                    highlightPath(p);
                }
            }
            return true;
        }

        // Draw the path the user makes on the canvas
        switch (event.getAction()) {
            // User starts drawing
            case MotionEvent.ACTION_DOWN:
                drawPath.moveTo(touchX, touchY);
                points.add(new ArrayList<>());
                points.get(points.size() - 1).add(new Point(touchX, touchY));
                break;
            // User continues drawing
            case MotionEvent.ACTION_MOVE:
                drawPath.lineTo(touchX, touchY);
                points.get(points.size() - 1).add(new Point(touchX, touchY));
                break;
            // User stops drawing
            case MotionEvent.ACTION_UP:
                // Save the path
                Path savedPath = new Path();
                savedPath.set(drawPath);
                paths.add(savedPath);

                // Draw and highlight the full path
                drawCanvas.drawPath(drawPath, drawPaint);
                highlightPath(savedPath);

                // Reset drawing
                drawPath.reset();
                isDrawing = false;
                drawCallback.drawCallback(savedPath);
                break;
            default:
                return false;
        }

        invalidate();
        return true;
    }

    // Initialize paint
    private void setupDrawing() {
        drawPath = new Path();

        drawPaint = new Paint();
        drawPaint.setColor(paintColor);
        drawPaint.setAntiAlias(true);
        drawPaint.setStrokeWidth(20);
        drawPaint.setStyle(Paint.Style.STROKE);
        drawPaint.setStrokeJoin(Paint.Join.ROUND);
        drawPaint.setStrokeCap(Paint.Cap.ROUND);

        highlightPaint = new Paint();
        highlightPaint.setColor(highlightColor);
        highlightPaint.setAntiAlias(true);
        highlightPaint.setStrokeWidth(40);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeJoin(Paint.Join.ROUND);
        highlightPaint.setStrokeCap(Paint.Cap.ROUND);
        highlightPaint.setAlpha(150);

        canvasPaint = new Paint(Paint.DITHER_FLAG);
    }

    /** Interface defining the callback for client classes. */
    public interface DrawCallback {
        void drawCallback(final Path path);
    }
}
