package com.windyice.qwerty;

import android.location.Criteria;
import android.util.Log;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Algorithm;
import org.opencv.core.Core;
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
import org.opencv.utils.Converters;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.sqrt;

/**
 * Created by 32699 on 2018/3/11.
 */

public class Camera {


    private static final Size defaultChessboardSize=new Size(7,5);
    private static final Size defaultChessboardGridWorldSpaceSize=
            new Size(Utils.chessboardWorldSpaceSize,Utils.chessboardWorldSpaceSize);
    private boolean mIsOkToCalibrate;

    // 这里究竟用那个?
    private List<List<Point3>> mObjectPointsW=new ArrayList<>(); // (K images x (7x5 point3f) on  3d world space
    public List<List<Point3>> getmObjectPointsW(){
        return mObjectPointsW;
    }
    private Mat mRotationMatrix=new Mat();
    private List<Mat> mRotationMatrixList=new ArrayList<>();
    private List<Point3> mTranslationMatrixList=new ArrayList<>();
    public List<Point3> getmTranslationMatrixList(){
        return mTranslationMatrixList;
    }
    public List<Mat> getmRotationMatrixList(){
        return mRotationMatrixList;
    }
    //private ArrayList<Mat> mObjectPointsW=new ArrayList<>();
    private List<List<Point>> mImagePoints=new ArrayList<>(); // (K images x (7x5 point2f) on 2d image
    private Size mImagePixelSize; // pixel size of chessboard image
    private List<Double> mDistortionCoeff; // k1,k2,p1,p2,k3
    private Mat mIntrinsicMatrix; // //similar to projection * viewport matrix
    //cv::Mat mRotationVector;//use Rodrigues transformation to convert to rotation matrix
    //cv::Mat mTranslationVector;
    private Mat mExtrinsicMatrix; // similar to view matrix ( R & T ),generate from rot&trans vector
    //private Mat rotationMatrix=new Mat();
    //private Mat transposeRotM;

    private String outString="";

    public Camera(){
        mIsOkToCalibrate=false;
        mIntrinsicMatrix=new Mat(3,3, CvType.CV_32FC1);
        mExtrinsicMatrix=new Mat(3,3,CvType.CV_32FC1);
    }

    public void RecognizeChessboard(String imagePath,boolean showImage)throws QwertyCameraException{
        Mat cbView1= Imgcodecs.imread(imagePath);

        if(cbView1==null){
            throw new QwertyCameraException("RecognizeChessboard : failed to open target image!");
            //Log.d("Qwerty_Camera","RecognizeChessboard : failed to open target image!");
        }
        RecognizeChessboard(cbView1,showImage);
    }

    @Override
    public String toString() {
        return outString;
    }

    public void RecognizeChessboard(Mat image, boolean showImage)throws QwertyCameraException{
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

            mImagePoints.add(cornerList.toList()); // 不知道这样会不会出问题?

            // corresponding World Space object points
            for(int j=0;j<defaultChessboardSize.height;++j){
                for(int i=0;i<defaultChessboardSize.width;++i){
                    //s hould Z coordinate keeps as 0? (2018.2.25) i think maybe it's not necessary.
                    final double gridWidth=defaultChessboardGridWorldSpaceSize.width;
                    final double gridHeight=defaultChessboardGridWorldSpaceSize.height;
                    final int cornerCountX=(int)defaultChessboardSize.width;
                    final int cornerCountY=(int)defaultChessboardSize.height;
                    Point3 correspondingObjectPointW=new Point3();
                    correspondingObjectPointW.x=(i-cornerCountX/2)*gridWidth;
                    correspondingObjectPointW.y=(cornerCountY/2-j)*gridHeight;
                    correspondingObjectPointW.z=0;
                    objectPointList.add(correspondingObjectPointW);
                }
            }
            mObjectPointsW.add(objectPointList);
            // update "Is it OK to calibrate?"
            // K images,N corners, constrains: 2NK >= 6K + 4
            // only the inequality satisfy that calibration can be performed
            // but taken noise into consideration, MORE images will be better
            if(mImagePoints.size()>5){
                mIsOkToCalibrate=true;
            }
        }
        else{
            throw new QwertyCameraException("RecognizeChessboard : failed to find chessboard pattern!");
        }
    }

    public boolean ReadyToCalibrate(){
        return mIsOkToCalibrate;
    }

    //camera calibration based on [Zhang00]'s method
    //several images taken from different view points are needed
    //distortion coeffecient & intrinsic parameters & extrinsic parameters
    //will be solved simultaneously with those
    //points information in given images.(3d object points and its projective 2d coord)
    public void Calibrate()throws QwertyCameraException{
        if(!mIsOkToCalibrate){
            throw new QwertyCameraException("Camera::Calibrate : not ready to calibrate!!");
        }

        // (3,3,CV_32FC1) CV_32FC1 : 32 bits float Channel 1
        Mat intrinsicMatrix=new Mat();// fx,fy,cx,cy, param which describes projection matrix/camera matrix
        Mat extrinsicMatrix=new Mat(3,3,CvType.CV_32FC1);// posture description
        // 这里也有变化,我把Float改成了Double
        ArrayList<Double> distortionCoeff=new ArrayList<>();// un-ideal feature(radial/tangential distortion) of real-world lens
        ArrayList<Mat> rotationVectors=new ArrayList<>();
        ArrayList<Mat> translationVectors=new ArrayList<>();

        // 数据转换一波
        // List<List<Point3>>其实存的是多张图，每张图里面有每张图对应的棋盘格边角点的三维坐标

        List<Mat> mObjectPointsWMat=new ArrayList<>();
        for(int i=0;i<mObjectPointsW.size();i++){
            mObjectPointsWMat.add(Converters.vector_Point3f_to_Mat(mObjectPointsW.get(i)));
        }
        List<Mat> mImagePointsMat=new ArrayList<>();
        for(int i=0;i<mImagePoints.size();i++){
            mImagePointsMat.add(Converters.vector_Point2f_to_Mat(mImagePoints.get(i)));
        }
        Mat distortionCoeffMat=new Mat();
        Calib3d.calibrateCamera(
                mObjectPointsWMat,
                mImagePointsMat,
                mImagePixelSize,
                intrinsicMatrix,
                distortionCoeffMat,
                rotationVectors,
                translationVectors,
                Calib3d.CALIB_FIX_ASPECT_RATIO
        );
        // 这里有个蜜汁转置？看行不行
        Converters.Mat_to_vector_double(distortionCoeffMat.t(),distortionCoeff);
        mDistortionCoeff=distortionCoeff;
        mIntrinsicMatrix=intrinsicMatrix;

        //emmm, actually i only need one extrinsic matrix as initiative posture
        //but to test the calibration correctness, all calibrated result will be saved to file
        StringBuilder stringBuilder=new StringBuilder();
        final double PI=3.141592653589;
        for(int i=0;i<rotationVectors.size();i++){
            Mat rotationMatrix=new Mat();
            Calib3d.Rodrigues(rotationVectors.get(i),rotationMatrix);
            Mat transposeRotM=rotationMatrix.t();
            mRotationMatrix=transposeRotM;
            mRotationMatrixList.add(mRotationMatrix);
            stringBuilder.append("calibration result for image ").append(i + 1).append(":\n");
            stringBuilder.append("Rotation matrix: \n");
            stringBuilder.append("    ").
                    append(transposeRotM.get(0,0)[0]).append(" ").
                    append(transposeRotM.get(0,1)[0]).append(" ").
                    append(transposeRotM.get(0,2)[0]).append("\n");
            stringBuilder.append("    ").
                    append(transposeRotM.get(1,0)[0]).append(" ").
                    append(transposeRotM.get(1,1)[0]).append(" ").
                    append(transposeRotM.get(1,2)[0]).append("\n");
            stringBuilder.append("    ").
                    append(transposeRotM.get(2,0)[0]).append(" ").
                    append(transposeRotM.get(2,1)[0]).append(" ").
                    append(transposeRotM.get(2,2)[0]).append("\n");
            double s2=transposeRotM.get(1,2)[0];
            double eulerAngleY=Math.atan2(transposeRotM.get(0,2)[0],transposeRotM.get(2,2)[0]);
            double eulerAngleX=Math.asin(-transposeRotM.get(1,2)[0]);
            double eulerAngleZ=(s2==1.0?PI / 2.0 : Math.asin(transposeRotM.get(1, 0)[0] / sqrt(1.0 - s2*s2)));

            //but for Noise3D's convenience, handness will be converted
            double noise3dEulerX=eulerAngleX;
            double noise3dEulerY=-eulerAngleY+PI;//z-axis inverse, rotate direction inverse
            double noise3dEulerZ=eulerAngleZ;
            stringBuilder.append("Euler Angle X,Y,Z for cv(right-hand system):").
                    append(eulerAngleX).append(", ").
                    append(eulerAngleY).append(", ").
                    append(eulerAngleZ).append("\n");
            stringBuilder.append("Euler Angle X,Y,Z for Noise3D (left-hand system):").
                    append(noise3dEulerX).append(", ").
                    append(noise3dEulerY).append(", ").
                    append(noise3dEulerZ).append("\n");

            // 注意这里没进行取负矩阵的操作！！实在没找到对应的方法
            Mat cameraWorldPos=matMul(transposeRotM,translationVectors.get(i),new Mat());
            double posX=-cameraWorldPos.get(0,0)[0];
            double posY=-cameraWorldPos.get(1,0)[0];
            double posZ=-cameraWorldPos.get(2,0)[0];

            mTranslationMatrixList.add(new Point3(posX,posY,posZ));

            // in noise3d, screen lies in YZ plane
            double noise3dPosX=posX;
            double noise3dPosY=posY;
            double noise3dPosZ=-posZ;

            stringBuilder.append("translation for cv camera: ").
                    append(posX).append(", ").
                    append(posY).append(", ").
                    append(posZ).append("\n");

            stringBuilder.append("translation for noise3d camera: ").
                    append(noise3dPosX).append(", ").
                    append(noise3dPosY).append(", ").
                    append(noise3dPosZ).append("\n");

            // TODO: 输出一堆东西(可能的优化，用一种数据记录一波)
        }
        outString=stringBuilder.toString();
    }

    /*
    * @param A 被乘矩阵
    * @param B 乘矩阵
    * @param C 结果矩阵
    * */
    public static Mat matMul(Mat A,Mat B,Mat C){
        Core.gemm(A,B,1.0,Mat.zeros(A.size(),A.type()),0.0,C);
        return C;
    }

    // 生成的Mat是只有1列，行数等于List.size()的矩阵
    private Mat point3ListToMat(List<Point3> point3List){
        Mat retMat=new Mat();
        for(int i=0;i<point3List.size();i++){
            Point3 eachPoint=point3List.get(i);
            double[] data={eachPoint.x,eachPoint.y,eachPoint.z};
            retMat.put(i,0,data);
        }
        return retMat;
    }

    private ArrayList<Point3> matToPoint3List(Mat mat){
        ArrayList<Point3> retList=new ArrayList<>();
        // 那么问题来了，这里的height指的是他的行数吗？
        for(int i=0;i<mat.size().height;i++){
            double data[]=mat.get(i,0);
            retList.add(new Point3(data[0],data[1],data[2]));
        }
        return retList;
    }

    private Mat pointListToMat(List<Point> pointList){
        Mat retMat=new Mat();
        for(int i=0;i<pointList.size();i++){
            Point eachPoint=pointList.get(i);
            double[] data={eachPoint.x,eachPoint.y};
            retMat.put(i,0,data);
        }
        return retMat;
    }

    private ArrayList<Point> matToPointList(Mat mat){
        ArrayList<Point> retList=new ArrayList<>();
        // 那么问题来了，这里的height指的是他的行数吗？
        for(int i=0;i<mat.size().height;i++){
            double data[]=mat.get(i,0);
            retList.add(new Point(data[0],data[1]));
        }
        return retList;
    }

    private Mat doubleListToMat(List<Double> doubleList){
        Mat retMat=new Mat();
        for(int i=0;i<doubleList.size();i++){
            retMat.put(i,0,doubleList.get(i));
        }
        return retMat;
    }

    private ArrayList<Double> matToDoubleList(Mat mat){
        ArrayList<Double> retList=new ArrayList<>();
        for(int i=0;i<mat.size().height;i++){
            retList.add(mat.get(i,0)[0]);
        }
        return retList;
    }
}
