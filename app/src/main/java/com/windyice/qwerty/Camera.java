package com.windyice.qwerty;

import android.location.Criteria;
import android.util.Log;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.osgi.OpenCVInterface;
import org.opencv.photo.Photo;

import java.util.ArrayList;

/**
 * Created by 32699 on 2018/3/11.
 */

public class Camera {


    private static final Size defaultChessboardSize=new Size(7,5);
    private static final Size defaultChessboardGridWorldSpaceSize=new Size(20.0,20.0);
    private boolean mIsOkToCalibrate;

    private ArrayList<ArrayList<Point3>> mObjectPointsW; // (K images x (7x5 point3f) on  3d world space
    private ArrayList<ArrayList<Point>> mImagePoints; // (K images x (7x5 point2f) on 2d image
    private Size mImagePixelSize; // pixel size of chessboard image
    private ArrayList<Float> mDistortionCoeff; // k1,k2,p1,p2,k3
    private Mat mIntrinsicMatrix; // //similar to projection * viewport matrix
    //cv::Mat mRotationVector;//use Rodrigues transformation to convert to rotation matrix
    //cv::Mat mTranslationVector;
    private Mat mExtrinsicMatrix; // similar to view matrix ( R & T ),generate from rot&trans vector

    public Camera(){
        mIsOkToCalibrate=false;
        mIntrinsicMatrix=new Mat(3,3, CvType.CV_32FC1);
        mExtrinsicMatrix=new Mat(3,3,CvType.CV_32FC1);
    }

    public boolean RecognizeChessboard(String imagePath,boolean showImage){
        Mat cbView1= Imgcodecs.imread(imagePath);
        if(cbView1==null){
            Log.d("Qwerty_Camera","RecognizeChessboard : failed to open target image!");
            return false;
        }
        return RecognizeChessboard(cbView1,showImage);
    }

    public boolean RecognizeChessboard(Mat image,boolean showImage){
        // pixel size of input image
        mImagePixelSize=new Size(image.size().width,image.size().height);

        // temp recognized corners list
        ArrayList<Point3> objectPointList=new ArrayList<>();
        // TODO: 这里有个变化！！！
        // ArrayList<Point> cornerList=new ArrayList<>();
        MatOfPoint2f cornerList=new MatOfPoint2f();

        boolean isPatternFound= org.opencv.calib3d.Calib3d.findChessboardCorners(image,defaultChessboardSize,cornerList, Calib3d.CALIB_CB_ADAPTIVE_THRESH | Calib3d.CALIB_CB_NORMALIZE_IMAGE);
        if(isPatternFound){
            // sub-pixel level corners fine-tuning
            Mat cbGrayView1=new Mat();
            Imgproc.cvtColor(image,cbGrayView1,Imgproc.COLOR_BGR2GRAY);
            Imgproc.cornerSubPix(
                    cbGrayView1,
                    cornerList,
                    new Size(10,10),
                    new Size(-1,-1),
                    new TermCriteria(
                            TermCriteria.EPS+TermCriteria.MAX_ITER,
                            30,
                            0.1));
            // draw markers to image and present to window
            if(showImage){
                Calib3d.drawChessboardCorners(image,defaultChessboardSize,cornerList,isPatternFound);
                // TODO:这里要用别的方法显示,但是这个image还是输出的image
                //cv::imshow("qwerty chessboard", image);
                //cv::waitKey(20150901);
            }

            // add to image points list (array of arrays)
            mImagePoints.add(new ArrayList<>(cornerList.toList())); // 不知道这样会不会出问题?
        }
    }
}
