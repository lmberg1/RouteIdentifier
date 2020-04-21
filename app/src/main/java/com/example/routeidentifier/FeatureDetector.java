package com.example.routeidentifier;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.features2d.AKAZE;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class FeatureDetector {
    private static AKAZE detector;
    private static DescriptorMatcher matcher;

    public FeatureDetector() {
        detector = AKAZE.create();
        matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
    }

    public Mat getHomography(Bitmap src, Bitmap query) {
        final Mat mat1 = bitmapToGrayMat(src);
        final Mat mat2 = bitmapToGrayMat(query);

        // Get the features and descriptors for the src image
        final Mat descriptors1 = new Mat();
        final MatOfKeyPoint keypoints1 = new MatOfKeyPoint();
        detector.detectAndCompute(mat1, new Mat(), keypoints1, descriptors1);

        // Get the features and descriptors for the query image
        final Mat descriptors2 = new Mat();
        final MatOfKeyPoint keypoints2 = new MatOfKeyPoint();
        detector.detectAndCompute(mat2, new Mat(), keypoints2, descriptors2);

        // Find the matches between them
        MatOfPoint2f[] matches = getGoodMatches(matcher, descriptors1, descriptors2, keypoints1, keypoints2);
        if (matches == null) return null;

        // Find the homography matrix between the matches
        Mat H = Calib3d.findHomography(matches[0], matches[1], Calib3d.RANSAC, 3);
        return H;
    }

    // Convert the bitmap to a gray OpenCV Mat
    public Mat bitmapToGrayMat(Bitmap bitmap) {
        final Mat mat = new Mat(bitmap.getWidth(), bitmap.getHeight(), CvType.CV_8UC4);
        Utils.bitmapToMat(bitmap, mat);
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGBA2GRAY);
        return mat;
    }

    // Get the points associated with the feature matches between the two sets of keypoints and
    // descriptors. Return null if no such matches exist
    private static MatOfPoint2f[] getGoodMatches(DescriptorMatcher matcher, Mat descriptors1, Mat descriptors2,
                                          MatOfKeyPoint keypoints1, MatOfKeyPoint keypoints2) {

        // Make sure descriptors aren't empty
        if (descriptors1.empty() || descriptors2.empty()) return null;

        // Match the features between the two images
        List<MatOfDMatch> knnMatches = new ArrayList<>();
        matcher.knnMatch(descriptors1, descriptors2, knnMatches, 2);

        // Convert keypoints to list
        List<KeyPoint> listOfKeypoints1 = keypoints1.toList();
        List<KeyPoint> listOfKeypoints2 = keypoints2.toList();

        // Find the good matches
        float ratioThresh = 0.8f;
        List<DMatch> listOfGoodMatches = new ArrayList<>();
        List<KeyPoint> listOfMatched1 = new ArrayList<>();
        List<KeyPoint> listOfMatched2 = new ArrayList<>();
        for (int i = 0; i < knnMatches.size(); i++) {
            if (knnMatches.get(i).rows() > 1) {
                DMatch[] matches = knnMatches.get(i).toArray();
                float dist1 = matches[0].distance;
                float dist2 = matches[1].distance;
                if (dist1 < ratioThresh * dist2) {
                    listOfGoodMatches.add(matches[0]);
                    listOfMatched1.add(listOfKeypoints1.get(matches[0].queryIdx));
                    listOfMatched2.add(listOfKeypoints2.get(matches[0].trainIdx));
                }
            }
        }
        MatOfDMatch goodMatches = new MatOfDMatch();
        goodMatches.fromList(listOfGoodMatches);

        // Return null if there are no good matches
        int n = listOfMatched1.size();
        if (n == 0) return null;

        // Find the points of good matches
        MatOfPoint2f src = new MatOfPoint2f();
        MatOfPoint2f dest = new MatOfPoint2f();
        List<Point> points1 = new ArrayList<>();
        List<Point> points2 = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            points1.add(listOfMatched1.get(i).pt);
            points2.add(listOfMatched2.get(i).pt);
        }
        src.fromList(points1);
        dest.fromList(points2);

        return new MatOfPoint2f[]{src, dest};
    }
}
