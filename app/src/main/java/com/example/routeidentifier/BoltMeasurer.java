package com.example.routeidentifier;

import android.graphics.Bitmap;
import android.graphics.RectF;

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY;

public class BoltMeasurer {
    // Store the center and diameters of all bolts selected so far
    private ArrayList<Point> boltCenters;
    private ArrayList<Integer> boltDiameters;

    // Store image in Bitmap and Mat formats
    private Bitmap imageBitmap;
    private Mat imageMat;
    private Mat grayImageMat;
    private int imageWidth;
    private int imageHeight;

    public BoltMeasurer() {}

    // Set the image currently being used in MeasureBoltDistanceActivity
    public void setImageBitmap(Bitmap imageBitmap) {
        // Reset arrays
        boltCenters = new ArrayList<>();
        boltDiameters = new ArrayList<>();

        // Save the bitmap and its dimensions
        this.imageBitmap = imageBitmap;
        imageWidth = imageBitmap.getWidth();
        imageHeight = imageBitmap.getHeight();

        // Save a mat version of the image
        imageMat = new Mat(imageWidth, imageHeight, CvType.CV_8UC4);
        Utils.bitmapToMat(imageBitmap, imageMat);

        // Save grayscale mat of the image
        grayImageMat = imageMat.clone();
        Imgproc.cvtColor(grayImageMat, grayImageMat, COLOR_RGBA2GRAY);
    }

    // Detects edges of bolt from bounding box of image zoom
    public Bitmap detectBolt(RectF zoom) {
        // Get bounds
        int left = (int) (zoom.left * imageWidth);
        int right = (int) (zoom.right * imageWidth);
        int top = (int) (zoom.top * imageHeight);
        int bottom = (int) (zoom.bottom * imageHeight);

        // Get the bolt box
        Rect bounds = new Rect(left, top, (right - left), (bottom - top));
        Mat boltBox = imageMat.submat(bounds);
        Mat grayBox = grayImageMat.submat(bounds);

        // Perform edge detection
        Imgproc.GaussianBlur(grayBox, grayBox, new Size(7, 7), 0);
        Imgproc.Canny(grayBox, grayBox, 50, 50);
        Imgproc.dilate(grayBox, grayBox, new Mat());
        Imgproc.erode(grayBox, grayBox, new Mat());

        // Find the contours
        ArrayList<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(grayBox, contours, new Mat(), 0, 2);

        // Find the largest contour
        Comparator<MatOfPoint> bySize = (MatOfPoint m1, MatOfPoint m2) -> m2.rows() - m1.rows();
        contours.sort(bySize);
        MatOfPoint largestContour = contours.get(0);

        // Draw largest contour
        Imgproc.drawContours(boltBox, contours, 0, new Scalar(255, 0, 0, 255), 2);

        // Get center of the contour
        Point cntr = getDiameter(largestContour, boltBox);
        boltCenters.add(new Point(cntr.x + bounds.x, cntr.y + bounds.y));

        // Measure the distance between each pair of bolts in the image
        int nBolts = boltCenters.size();
        if (nBolts > 1) {
            // Bolt centers
            Point p1 = boltCenters.get(nBolts - 1);
            Point p2 = boltCenters.get(nBolts - 2);

            // Bolt diameters (pixels)
            int diam1 = boltDiameters.get(nBolts - 1);
            int diam2 = boltDiameters.get(nBolts - 2);

            // Convert distance between the bolts from pixels to feet
            double px = Math.sqrt(getEuclDist(p1, p2));
            double dist = 2 * 2.25 * px / (diam1 + diam2) / 12;

            // Draw a line between the bolts and display the distance
            Point mid = new Point((p1.x + p2.x) / 2, (p1.y + p2.y) / 2);
            String txt = String.format(Locale.US, "%.1f Feet", dist);
            Imgproc.putText(imageMat, txt, mid, 1, 8, new Scalar(255, 0, 0, 255), 5);
            Imgproc.line(imageMat, p1, p2, new Scalar(0, 0, 0, 255), 5);
        }

        // Return image with bolt measurement annotations
        Utils.matToBitmap(imageMat, imageBitmap);
        return imageBitmap;
    }

    // Distance between a point and line
    private static double getLineDist(double[] line, Point p) {
        // Get line parameters in the form ax + by + c = 0
        double a = line[0];
        double b = -1;
        double c = line[1];
        return Math.abs(a*p.x + b*p.y + c) / Math.sqrt(a*a + b*b);
    }

    // Distance between two points
    private static double getEuclDist(Point p1, Point p2) {
        return Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2);
    }

    // Estimate the diameter of a contour using principle component analysis
    private Point getDiameter(MatOfPoint contour, Mat img) {
        List<Point> pts = contour.toList();

        // Find center and equation of the pca line
        double[] pca_info = getOrientation(pts, img);
        double[] line = new double[]{pca_info[0], pca_info[1]};
        Point cntr = new Point(pca_info[2], pca_info[3]);

        // Only keep points in the contour lying along the line
        double threshold = img.rows() / 20.0;
        ArrayList<Point> good_pts = new ArrayList<>();
        for (Point p : pts) {
            if (getLineDist(line, p) < threshold) {
                good_pts.add(p);
            }
        }

        // Check whether diameter should be in horizontal or vertical direction
        ArrayList<Point> hor = (ArrayList<Point>) good_pts.clone();
        ArrayList<Point> vert = good_pts;

        // Sort points from left to right
        hor.sort((Point o1, Point o2) -> {
            double sign1 = (o1.x == cntr.x) ? 0 : (o1.x - cntr.x) / Math.abs(o1.x - cntr.x);
            double sign2 = (o2.x == cntr.x) ? 0 : (o2.x - cntr.x) / Math.abs(o2.x - cntr.x);
            return (int) (sign1 * getEuclDist(o2, cntr) - sign2 * getEuclDist(o1, cntr));
        });

        // Sort points from top to bottom
        vert.sort((Point o1, Point o2) -> {
            double sign1 = (o1.y == cntr.y) ? 0 : (o1.y - cntr.y) / Math.abs(o1.y - cntr.y);
            double sign2 = (o2.y == cntr.y) ? 0 : (o2.y - cntr.y) / Math.abs(o2.y - cntr.y);
            return (int) (sign1 * getEuclDist(o2, cntr) - sign2 * getEuclDist(o1, cntr));
        });

        // Get distance between two most extreme horizontal points
        Point h1 = hor.get(0);
        Point h2 = hor.get(hor.size() - 1);
        int dist1 = (int) Math.sqrt(getEuclDist(h1, h2));

        // Get distance between two most extrement vertical points
        Point v1 = vert.get(0);
        Point v2 = vert.get(vert.size() - 1);
        int dist2 = (int) Math.sqrt(getEuclDist(v1, v2));

        // Determine the largest distance
        if (dist2 > dist1) {
            h1 = v1;
            h2 = v2;
            dist1 = dist2;
        }

        // Annotate the image with the bolt diameter
        Imgproc.circle(img, h1 , 3, new Scalar(0, 0, 0, 255), -1);
        Imgproc.circle(img, h2 , 3, new Scalar(0, 0, 0, 255), -1);
        Imgproc.line(img, h1, h2, new Scalar(0, 255, 0));
        Imgproc.circle(img, cntr, 3, new Scalar(255, 0, 255), -1);

        // Save bolt centers and diameters
        boltDiameters.add(dist1);

        return cntr;

    }

    // Based off of: https://docs.opencv.org/3.4/d1/dee/tutorial_introduction_to_pca.html
    private static double[] getOrientation(List<Point> pts, Mat img) {
        // Construct a buffer used by the pca analysis
        int sz = pts.size();
        Mat dataPts = new Mat(sz, 2, CvType.CV_64F);
        double[] dataPtsData = new double[(int) (dataPts.total() * dataPts.channels())];
        for (int i = 0; i < dataPts.rows(); i++) {
            dataPtsData[i * dataPts.cols()] = pts.get(i).x;
            dataPtsData[i * dataPts.cols() + 1] = pts.get(i).y;
        }
        dataPts.put(0, 0, dataPtsData);
        // Perform PCA analysis
        Mat mean = new Mat();
        Mat eigenvectors = new Mat();
        Core.PCACompute(dataPts, mean, eigenvectors);
        double[] meanData = new double[(int) (mean.total() * mean.channels())];
        mean.get(0, 0, meanData);
        // Store the center of the object
        Point cntr = new Point(meanData[0], meanData[1]);
        // Store the eigenvalues and eigenvectors
        double[] eigenvectorsData = new double[(int) (eigenvectors.total() * eigenvectors.channels())];
        double[] eigenvaluesData = new double[]{1000, 1000, 1000, 1000};

        eigenvectors.get(0, 0, eigenvectorsData);
        // Draw the principal components
        Point p1 = new Point(cntr.x + 0.02 * eigenvectorsData[0] * eigenvaluesData[0],
                cntr.y + 0.02 * eigenvectorsData[1] * eigenvaluesData[0]);
        Point p2 = new Point(cntr.x - 0.02 * eigenvectorsData[2] * eigenvaluesData[1],
                cntr.y - 0.02 * eigenvectorsData[3] * eigenvaluesData[1]);

        // Convert eigenvector to line equation
        double slope = (cntr.y - p1.y) / (cntr.x - p1.x);
        double yint = cntr.y - slope * cntr.x;
        return new double[]{slope, yint, cntr.x, cntr.y};
    }
}
