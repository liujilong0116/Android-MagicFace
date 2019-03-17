//又臭又烂又长的C++代码，懒得整理了，反正功能上没有问题，改动不大
#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <iostream>
#include <opencv2/opencv.hpp>
#include <fstream>
#include <dlib/opencv.h>
#include <dlib/image_processing.h>
using namespace cv;
using namespace std;

#define MAKE_RGBA(r, g, b, a) (((a) << 24) | ((r) << 16) | ((g) << 8) | (b))
#define RGBA_A(p) (((p) & 0xFF000000) >> 24)
#define M_PI 3.1415926

CascadeClassifier ccf;//opencv的寻找人脸的模型
dlib::shape_predictor pose_model;//dlib的68点寻找模型
Mat pics_change[2];//变脸里用到的两个图片都存到这里，就不用多次加载，用的时候都加上.clone()防止其改变
Mat pics_gif[10];//GIF的10个不同程度的图片
Mat pics[10];//平均脸的最多10张图
vector<vector<Point2f>> allpoints;
Mat exchangepic;


//平均脸的时候把图片尺寸都设置为600*800
Mat SetSize( Mat pic) {
    Size sSize = Size(600 , 800);
    Mat pDes = Mat(sSize, CV_32S);
    resize(pic, pDes, sSize);
    pDes.convertTo(pDes,CV_8U);
    return pDes;
}

void similarityTransform(std::vector<cv::Point2f>& inPoints, std::vector<cv::Point2f>& outPoints, cv::Mat &tform)
{
    double s60 = sin(60 * M_PI / 180.0);
    double c60 = cos(60 * M_PI / 180.0);

    vector <Point2f> inPts = inPoints;
    vector <Point2f> outPts = outPoints;

    inPts.push_back(cv::Point2f(0, 0));
    outPts.push_back(cv::Point2f(0, 0));

    inPts[2].x = c60 * (inPts[0].x - inPts[1].x) - s60 * (inPts[0].y - inPts[1].y) + inPts[1].x;
    inPts[2].y = s60 * (inPts[0].x - inPts[1].x) + c60 * (inPts[0].y - inPts[1].y) + inPts[1].y;

    outPts[2].x = c60 * (outPts[0].x - outPts[1].x) - s60 * (outPts[0].y - outPts[1].y) + outPts[1].x;
    outPts[2].y = s60 * (outPts[0].x - outPts[1].x) + c60 * (outPts[0].y - outPts[1].y) + outPts[1].y;

    tform = cv::estimateRigidTransform(inPts, outPts, false);
}

//用仿射变换实现变脸
void applyAffineTransform(Mat &warpImage, Mat &src, vector<Point2f> &srcTri, vector<Point2f> &dstTri)
{
    //给定一对三角形，找到仿射变换
    Mat warpMat = getAffineTransform(srcTri, dstTri);
    //把刚找到的仿射变换用于src
    warpAffine(src, warpImage, warpMat, warpImage.size(), INTER_LINEAR, BORDER_REFLECT_101);
}

// 对设定点的计算Delaunay三角形
// 返回每个三角形的3点索引向量
static void calculateDelaunayTriangles(Rect rect, vector<Point2f> &points, vector< vector<int> > &delaunayTri) {

    // 创建一个 Subdiv2D 变量
    Subdiv2D subdiv(rect);

    // 把点插入到 subdiv 里
    for (vector<Point2f>::iterator it = points.begin(); it != points.end(); it++)
        subdiv.insert(*it);

    vector<Vec6f> triangleList;
    subdiv.getTriangleList(triangleList);
    vector<Point2f> pt(3);
    vector<int> ind(3);

    for (size_t i = 0; i < triangleList.size(); i++)
    {
        Vec6f t = triangleList[i];
        pt[0] = Point2f(t[0], t[1]);
        pt[1] = Point2f(t[2], t[3]);
        pt[2] = Point2f(t[4], t[5]);

        if (rect.contains(pt[0]) && rect.contains(pt[1]) && rect.contains(pt[2])) {
            for (int j = 0; j < 3; j++)
                for (size_t k = 0; k < points.size(); k++)
                    if (abs(pt[j].x - points[k].x) < 1.0 && abs(pt[j].y - points[k].y) < 1)
                        ind[j] = k;
            delaunayTri.push_back(ind);
        }
    }
}

//找到一张脸的68个点  然后再加上边框上的8个点  存到points里
vector<Point2f> find_save_points(Mat img)
{
    vector<Rect> faces1;
    vector<Point2f> points;
    Mat gray;
    cvtColor(img, gray, CV_BGR2GRAY);
    equalizeHist(gray, gray);
    ccf.detectMultiScale(gray, faces1, 1.1, 3, 0, Size(150, 150), Size(1000, 1000));

    dlib::cv_image<dlib::bgr_pixel> cimg(img);
    dlib::rectangle faces;
    faces.set_top(faces1[0].tl().y);
    faces.set_bottom(faces1[0].br().y);
    faces.set_left(faces1[0].tl().x);
    faces.set_right(faces1[0].br().x);
    vector<dlib::full_object_detection> shapes;
    shapes.push_back(pose_model(cimg, faces));

    for (int i = 0; i < 68; i++)
        points.push_back(Point(shapes[0].part(i).x(), shapes[0].part(i).y()));

    return points;
}

// 弯曲和alpha 参数混合将两张人脸加在一起
void warpTriangle(Mat &img1, Mat &img2, vector<Point2f> &t1, vector<Point2f> &t2)
{
    //定义两个矩形类变量r1  r2   值为将t1和t2里所有的点围起来的矩形
    //有四个变量 x，y，width，height 左上角坐标以及宽高
    Rect r1 = boundingRect(t1);
    Rect r2 = boundingRect(t2);

    // 由相应矩形的左上角偏移点
    vector<Point2f> t1Rect, t2Rect;
    vector<Point> t2RectInt;
    for (int i = 0; i < 3; i++)
    {
        t1Rect.push_back(Point2f(t1[i].x - r1.x, t1[i].y - r1.y));
        t2Rect.push_back(Point2f(t2[i].x - r2.x, t2[i].y - r2.y));
        t2RectInt.push_back(Point(t2[i].x - r2.x, t2[i].y - r2.y)); //填充聚合体
    }

    // 三角填充法
    Mat mask = Mat::zeros(r2.height, r2.width, CV_32FC3);
    fillConvexPoly(mask, t2RectInt, Scalar(1.0, 1.0, 1.0), 16, 0);

    // 将 warpImage 应用于小矩形贴片
    Mat img1Rect;
    //保留的人脸区域赋给img1Rect
    img1(r1).copyTo(img1Rect);
    Mat img2Rect = Mat::zeros(r2.height, r2.width, img1Rect.type());

    applyAffineTransform(img2Rect, img1Rect, t1Rect, t2Rect);

    multiply(img2Rect, mask, img2Rect);
    multiply(img2(r2), Scalar(1.0, 1.0, 1.0) - mask, img2(r2));
    img2(r2) = img2(r2) + img2Rect;
}
//判断是否有人脸，有则将68点保存，好像用不着判断，之前就判断过了，懒得改了
bool findlandmark(Mat img,vector<Point2f> &points){
    vector<Rect> faces;
    Mat gray;
    cvtColor(img, gray, CV_BGR2GRAY);
    equalizeHist(gray, gray);
    ccf.detectMultiScale(gray, faces, 1.1, 3, 0, Size(150, 150), Size(1000, 1000));
    if(faces.empty())
        return false;
    else{
    dlib::cv_image<dlib::bgr_pixel> cimg(img);
    dlib::rectangle facesd;
    facesd.set_top(faces[0].tl().y);
    facesd.set_bottom(faces[0].br().y);
    facesd.set_left(faces[0].tl().x);
    facesd.set_right(faces[0].br().x);
    vector<dlib::full_object_detection> shapes;
    shapes.push_back(pose_model(cimg, facesd));

    for (int i = 0; i < 68; i++)
    {
        points.push_back(Point2f(shapes[0].part(i).x(), shapes[0].part(i).y()));
    }
    return true;
    }
}

void constrainPoint(Point2f &p, Size sz)
{
    p.x = min(max((double)p.x, 0.0), (double)(sz.width - 1));
    p.y = min(max((double)p.y, 0.0), (double)(sz.height - 1));
}

//模型加载
extern "C"
JNIEXPORT void JNICALL
Java_com_example_administrator_try_1again_select_createswap(JNIEnv *env, jobject instance) {
    ccf.load("/sdcard/faceswap/haarcascade_frontalface_alt.xml");
    dlib::deserialize("/sdcard/faceswap/shape_predictor_68_face_landmarks.dat") >> pose_model;
}

//把bitmap转换成mat，用于换脸、融合、GIF
extern "C"
JNIEXPORT void JNICALL
Java_com_example_administrator_try_1again_MainActivity_bitmaptomat(JNIEnv *env, jobject instance,
                                                                   jobject bitmap, jint whice_pic) {

    // TODO
    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    AndroidBitmap_getInfo(env, bitmap, &info);
    void *pixels = NULL;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);
    Mat img(info.height, info.width, CV_8UC4, pixels);
    cvtColor(img, img, CV_BGRA2RGB);

    pics_change[whice_pic] = img.clone();

}

//换脸程序，为啥用bool类型忘了，可能不需要，八成是顺手复制函数名忘了改，一下的bool可能都是这样
extern "C"
jboolean JNICALL
Java_com_example_administrator_try_1again_swap_faceswap(JNIEnv *env, jclass type,
                                                                     jobject bitmap) {

        //将bitmap转化成img1
        AndroidBitmapInfo info;
        memset(&info, 0, sizeof(info));
        AndroidBitmap_getInfo(env, bitmap, &info);
        void *pixels = NULL;
        AndroidBitmap_lockPixels(env, bitmap, &pixels);


        Mat img1 = pics_change[0].clone();
        Mat img2 = pics_change[1].clone();
        Mat img1Warped = img2.clone();
        Mat img2temp = img2.clone();
        Mat output;

        //获取人脸的68个点并存入points1以及points2中
        vector<Point2f> points1, points2;
        if(findlandmark(img1,points1)&& findlandmark(img2,points2)){
            //图片改成浮点型
            img1.convertTo(img1, CV_32F);
            img1Warped.convertTo(img1Warped, CV_32F);
            img2temp.convertTo(img2temp, CV_32F);


            // 寻找凸包  hull1和hull2分别存储凸包的点
            vector<Point2f> hull1;
            vector<Point2f> hull2;
            vector<int> hullIndex;

            convexHull(points2, hullIndex, false, false);

            for (int i = 0; i < hullIndex.size(); i++)
            {
                hull1.push_back(points1[hullIndex[i]]);
                hull2.push_back(points2[hullIndex[i]]);
            }


            // 找到凸壳点的Delaunay三角剖分
            vector< vector<int> > dt;
            Rect rect(0, 0, img1Warped.cols, img1Warped.rows);
            calculateDelaunayTriangles(rect, hull2, dt);

            //在Delaunay三角形上的应用仿射变换
            //将图一的脸变到图二的对应位置   相当于在图一的复制图img1warped上的图二人脸区域有了一个图一人脸的变形
            for (size_t i = 0; i < dt.size(); i++)
            {
                vector<Point2f> t1, t2;
                // 得到图一图二上对应三角形的点
                for (size_t j = 0; j < 3; j++)
                {
                    t1.push_back(hull1[dt[i][j]]);
                    t2.push_back(hull2[dt[i][j]]);
                }

                warpTriangle(img1, img1Warped, t1, t2);
            }


            //把图二的凸包的点存入hull8U（应该是一个8U的浮点型)
            vector<Point> hull8U;
            for (int i = 0; i < hull2.size(); i++)
            {
                Point pt(hull2[i].x, hull2[i].y);
                hull8U.push_back(pt);
            }

            Mat mask = Mat::zeros(img2.rows, img2.cols, img2.depth());
            fillConvexPoly(mask, &hull8U[0], hull8U.size(), Scalar(255, 255, 255));

            // 无缝克隆
            Rect r = boundingRect(hull2);
            //center为人脸二的中心点
            Point center = (r.tl() + r.br()) / 2;

            img1Warped.convertTo(img1Warped, CV_8UC3);
            seamlessClone(img1Warped, img2, mask, center, output, NORMAL_CLONE);
        } else
        {
            return false;
        }


        //将图像输出到bitmap1
        int a = 0, r1 = 0, g = 0, b = 0;
        for (int y = 0; y < info.height; ++y) {
            // From left to right
            for (int x = 0; x < info.width; ++x) {
                int *pixel = NULL;
                pixel = ((int *) pixels) + y * info.width + x;
                r1 = output.at<Vec3b>(y, x)[0];
                g = output.at<Vec3b>(y, x)[1];
                b = output.at<Vec3b>(y, x)[2];
                a = RGBA_A(*pixel);
                *pixel = MAKE_RGBA(r1, g, b, a);
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}
//融合程序，和换脸不同，没有找凸包，直接用68个点找Delaunay，使得五官对齐
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_administrator_try_1again_vary_facevary(JNIEnv *env, jobject instance,
                                                        jobject bitmap, jdouble alpha) {

    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    AndroidBitmap_getInfo(env, bitmap, &info);
    void *pixels = NULL;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    Mat img1 = pics_change[0].clone();
    Mat img2 = pics_change[1].clone();

    Mat img1Warped = img2.clone();
    Mat img2temp = img2.clone();
    Mat output;

    //获取人脸的68个点并存入points1以及points2中
    vector<Point2f> points1, points2;
    if(findlandmark(img1,points1)&& findlandmark(img2,points2)){

        //图片改成浮点型
        img1.convertTo(img1, CV_32F);
        img1Warped.convertTo(img1Warped, CV_32F);
        img2temp.convertTo(img2temp, CV_32F);


        // 寻找凸包  hull1和hull2分别存储凸包的点
        vector<Point2f> hull1;
        vector<Point2f> hull2;
        vector<int> hullIndex;

        convexHull(points2, hullIndex, false, false);

        for (int i = 0; i < hullIndex.size(); i++)
        {
            hull1.push_back(points1[hullIndex[i]]);
            hull2.push_back(points2[hullIndex[i]]);
        }

        //不用凸包，直接找68点的Delaunay
        vector< vector<int> > dt;
        Rect rect(0, 0, img1Warped.cols, img1Warped.rows);
        calculateDelaunayTriangles(rect, points2, dt);

        //在Delaunay三角形上的应用仿射变换
        //将图一的脸变到图二的对应位置   相当于在图一的复制图img1warped上的图二人脸区域有了一个图一人脸的变形
        for (size_t i = 0; i < dt.size(); i++)
        {
            vector<Point2f> t1, t2;
            // 得到图一图二上对应三角形的点
            for (size_t j = 0; j < 3; j++)
            {
                t1.push_back(points1[dt[i][j]]);
                t2.push_back(points2[dt[i][j]]);
            }

            warpTriangle(img1, img1Warped, t1, t2);
        }

        //两张人脸的融合比例
        img1Warped = alpha*img1Warped.clone() + (1-alpha)*img2temp.clone();


        // 计算mask（暂时不知道怎么翻译）
        //把图二的凸包的点存入hull8U（应该是一个8U的浮点型)
        vector<Point> hull8U;
        for (int i = 0; i < hull2.size(); i++)
        {
            Point pt(hull2[i].x, hull2[i].y);
            hull8U.push_back(pt);
        }

        Mat mask = Mat::zeros(img2.rows, img2.cols, img2.depth());
        fillConvexPoly(mask, &hull8U[0], hull8U.size(), Scalar(255, 255, 255));

        // 无缝克隆
        Rect r = boundingRect(hull2);
        //center为人脸二的中心点
        Point center = (r.tl() + r.br()) / 2;

        img1Warped.convertTo(img1Warped, CV_8UC3);
        seamlessClone(img1Warped, img2, mask, center, output, NORMAL_CLONE);
    } else
    {
        return false;
    }


    //将图像输出到bitmap1
    int a = 0, r1 = 0, g = 0, b = 0;
    for (int y = 0; y < info.height; ++y) {
        // From left to right
        for (int x = 0; x < info.width; ++x) {
            int *pixel = NULL;
            pixel = ((int *) pixels) + y * info.width + x;
            r1 = output.at<Vec3b>(y, x)[0];
            g = output.at<Vec3b>(y, x)[1];
            b = output.at<Vec3b>(y, x)[2];
            a = RGBA_A(*pixel);
            *pixel = MAKE_RGBA(r1, g, b, a);
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return true;
}

//制作GIF图之前做好10张不同程度的融合图
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_administrator_try_1again_gif_creatpic(JNIEnv *env, jobject instance) {

    Mat img1 = pics_change[0].clone();
    Mat img2 = pics_change[1].clone();

    Mat img1Warped = img2.clone();
    Mat img2temp = img2.clone();
    Mat output;

    //获取人脸的68个点并存入points1以及points2中
    vector<Point2f> points1, points2;
    if(findlandmark(img1,points1)&& findlandmark(img2,points2)){

        //图片改成浮点型
        img1.convertTo(img1, CV_32F);
        img1Warped.convertTo(img1Warped, CV_32F);
        img2temp.convertTo(img2temp, CV_32F);


        // 寻找凸包  hull1和hull2分别存储凸包的点
        vector<Point2f> hull1;
        vector<Point2f> hull2;
        vector<int> hullIndex;

        convexHull(points2, hullIndex, false, false);

        for (int i = 0; i < hullIndex.size(); i++)
        {
            hull1.push_back(points1[hullIndex[i]]);
            hull2.push_back(points2[hullIndex[i]]);
        }


        // 找到凸壳点的Delaunay三角剖分
        vector< vector<int> > dt;
        Rect rect(0, 0, img1Warped.cols, img1Warped.rows);
        calculateDelaunayTriangles(rect, points2, dt);

        //在Delaunay三角形上的应用仿射变换
        //将图一的脸变到图二的对应位置   相当于在图一的复制图img1warped上的图二人脸区域有了一个图一人脸的变形
        for (size_t i = 0; i < dt.size(); i++)
        {
            vector<Point2f> t1, t2;
            // 得到图一图二上对应三角形的点
            for (size_t j = 0; j < 3; j++)
            {
                t1.push_back(points1[dt[i][j]]);
                t2.push_back(points2[dt[i][j]]);
            }

            warpTriangle(img1, img1Warped, t1, t2);
        }

        //两张人脸的融合比例
        img1Warped = img1Warped.clone() ;


        // 计算mask（暂时不知道怎么翻译）
        //把图二的凸包的点存入hull8U（应该是一个8U的浮点型)
        vector<Point> hull8U;
        for (int i = 0; i < hull2.size(); i++)
        {
            Point pt(hull2[i].x, hull2[i].y);
            hull8U.push_back(pt);
        }

        Mat mask = Mat::zeros(img2.rows, img2.cols, img2.depth());
        fillConvexPoly(mask, &hull8U[0], hull8U.size(), Scalar(255, 255, 255));

        // 无缝克隆
        Rect r = boundingRect(hull2);
        //center为人脸二的中心点
        Point center = (r.tl() + r.br()) / 2;

        img1Warped.convertTo(img1Warped, CV_8UC3);
        seamlessClone(img1Warped, img2, mask, center, output, NORMAL_CLONE);

        for(int i = 1 ; i <= 10 ; i ++)
            pics_gif[i-1] = 0.1 * i * output.clone() + 0.1 * (10-i)*img2.clone();
    } else
    {
        return false;
    }
    return true;

}


//获取之前创建的10张不同程度的融合图去制作GIF
extern "C"
JNIEXPORT void JNICALL
Java_com_example_administrator_try_1again_gif_getpic(JNIEnv *env, jobject instance, jobject bitmap,
                                                     jint num) {

    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    AndroidBitmap_getInfo(env, bitmap, &info);
    void *pixels = NULL;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);
    int a = 0, r1 = 0, g = 0, b = 0;
    Mat output;
    output = pics_gif[num - 1].clone();

    for (int y = 0; y < info.height; ++y) {
        // From left to right
        for (int x = 0; x < info.width; ++x) {
            int *pixel = NULL;
            pixel = ((int *) pixels) + y * info.width + x;
            r1 = output.at<Vec3b>(y, x)[0];
            g = output.at<Vec3b>(y, x)[1];
            b = output.at<Vec3b>(y, x)[2];
            a = RGBA_A(*pixel);
            *pixel = MAKE_RGBA(r1, g, b, a);
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);

}

//判断图里是否有人脸
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_administrator_try_1again_MainActivity_judge(JNIEnv *env, jobject instance
                                                             ) {
    Mat img1 = pics_change[0].clone();
    vector<Rect> faces;
    Mat gray;
    cvtColor(img1, gray, CV_BGR2GRAY);
    equalizeHist(gray, gray);
    ccf.detectMultiScale(gray, faces, 1.1, 3, 0, Size(150, 150), Size(1000, 1000));

    Mat img2 = pics_change[1].clone();
    vector<Rect> faces2;
    Mat gray2;
    cvtColor(img2, gray2, CV_BGR2GRAY);
    equalizeHist(gray2, gray2);
    ccf.detectMultiScale(gray2, faces2, 1.1, 3, 0, Size(150, 150), Size(1000, 1000));

    if(faces.empty() || faces2.empty())
        return false;
    else
        return true;
}


//将选中的图片存入pics[]中，并调整尺寸为600*800
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_administrator_try_1again_average_createpic(JNIEnv *env, jobject instance,
                                                               jobject bitmap, jint no) {

    // TODO
    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    AndroidBitmap_getInfo(env, bitmap, &info);
    void *pixels = NULL;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);
    Mat img(info.height, info.width, CV_8UC4, pixels);
    cvtColor(img, img, CV_BGRA2RGB);

    vector<Rect> faces;
    Mat gray;
    cvtColor(img, gray, CV_BGR2GRAY);
    equalizeHist(gray, gray);
    ccf.detectMultiScale(gray, faces, 1.1, 3, 0, Size(150, 150), Size(1000, 1000));

    if(faces.empty())
        return false;
    else
    {
        pics[no] = img.clone();
        return true;
    }
}

//制作平均脸
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_administrator_try_1again_average_facemorph(JNIEnv *env, jobject instance,
                                                                jobject bitmap, jdouble num) {

    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    AndroidBitmap_getInfo(env, bitmap, &info);
    void *pixels = NULL;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    double num_pic = num;

    // Read images
    vector<Mat> images;
    for (size_t i = 0; i < num_pic; i++)
    {
        Mat img = pics[i].clone();
        img.convertTo(img, CV_32FC3, 1 / 255.0);
        images.push_back(img);
    }
    // Dimensions of output image
    int w = 600;
    int h = 600;
    // Read points
    vector<vector<Point2f> > allPoints;
    for(int i = 0;i < num_pic ;i++)
        allPoints.push_back(find_save_points(pics[i]));

    int numImages = images.size();

    // Eye corners
    vector<Point2f> eyecornerDst, eyecornerSrc;
    eyecornerDst.push_back(Point2f(0.3*w, h / 3));
    eyecornerDst.push_back(Point2f(0.7*w, h / 3));

    eyecornerSrc.push_back(Point2f(0, 0));
    eyecornerSrc.push_back(Point2f(0, 0));

    // Space for normalized images and points.
    vector <Mat> imagesNorm;
    vector < vector <Point2f> > pointsNorm;

    // Space for average landmark points
    vector <Point2f> pointsAvg(allPoints[0].size());

    // 8 Boundary points for Delaunay Triangulation
    vector <Point2f> boundaryPts;
    boundaryPts.push_back(Point2f(0, 0));
    boundaryPts.push_back(Point2f(w / 2, 0));
    boundaryPts.push_back(Point2f(w - 1, 0));
    boundaryPts.push_back(Point2f(w - 1, h / 2));
    boundaryPts.push_back(Point2f(w - 1, h - 1));
    boundaryPts.push_back(Point2f(w / 2, h - 1));
    boundaryPts.push_back(Point2f(0, h - 1));
    boundaryPts.push_back(Point2f(0, h / 2));

    // Warp images and trasnform landmarks to output coordinate system,
    // and find average of transformed landmarks.

    for (size_t i = 0; i < images.size(); i++)
    {

        vector <Point2f> points = allPoints[i];

        // The corners of the eyes are the landmarks number 36 and 45
        eyecornerSrc[0] = allPoints[i][36];
        eyecornerSrc[1] = allPoints[i][45];

        // Calculate similarity transform
        Mat tform;
        similarityTransform(eyecornerSrc, eyecornerDst, tform);

        // Apply similarity transform to input image and landmarks
        Mat img = Mat::zeros(h, w, CV_32FC3);
        warpAffine(images[i], img, tform, img.size());
        transform(points, points, tform);

        //计算个点的平均值
        for (size_t j = 0; j < points.size(); j++)
        {
            pointsAvg[j] += points[j] * (1.0 / numImages);
        }

        // Append boundary points. Will be used in Delaunay Triangulation
        for (size_t j = 0; j < boundaryPts.size(); j++)
        {
            points.push_back(boundaryPts[j]);
        }

        pointsNorm.push_back(points);
        imagesNorm.push_back(img);


    }

    // Append boundary points to average points.
    for (size_t j = 0; j < boundaryPts.size(); j++)
    {
        pointsAvg.push_back(boundaryPts[j]);
    }



    // Calculate Delaunay triangles
    Rect rect(0, 0, w, h);
    vector< vector<int> > dt;
    calculateDelaunayTriangles(rect, pointsAvg, dt);

    // Space for output image
    Mat output = Mat::zeros(h, w, CV_32FC3);
    Size size(w, h);

    // Warp input images to average image landmarks

    for (size_t i = 0; i < numImages; i++)
    {
        Mat img = Mat::zeros(h, w, CV_32FC3);
        // Transform triangles one by one
        for (size_t j = 0; j < dt.size(); j++)
        {
            // Input and output points corresponding to jth triangle
            vector<Point2f> tin, tout;
            for (int k = 0; k < 3; k++)
            {
                Point2f pIn = pointsNorm[i][dt[j][k]];
                constrainPoint(pIn, size);

                Point2f pOut = pointsAvg[dt[j][k]];
                constrainPoint(pOut, size);

                tin.push_back(pIn);
                tout.push_back(pOut);
            }

            warpTriangle(imagesNorm[i], img, tin, tout);
        }

        // Add image intensities for averaging
        output = output.clone() + img.clone();

    }

    // Divide by numImages to get average
    output = output / (double)numImages;
    output.convertTo(output,CV_8U,255);

    //将图像输出到bitmap1
    int a = 0, r1 = 0, g = 0, b = 0;
    for (int y = 0; y < info.height; ++y) {
        // From left to right
        for (int x = 0; x < info.width; ++x) {
            int *pixel = NULL;
            pixel = ((int *) pixels) + y * info.width + x;
            r1 = output.at<Vec3b>(y, x)[0];
            g = output.at<Vec3b>(y, x)[1];
            b = output.at<Vec3b>(y, x)[2];
            a = RGBA_A(*pixel);
            *pixel = MAKE_RGBA(r1, g, b, a);
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return true;

}

void exchange(Mat src1,Mat src2, vector<Point2f> points1, vector<Point2f> points2,Mat &output)
{
    Mat img1 = src1.clone();
    Mat img2 = src2.clone();
    Mat img1Warped = img2.clone();
    Mat img2temp = img2.clone();

    //图片改成浮点型
    img1.convertTo(img1, CV_32F);
    img1Warped.convertTo(img1Warped, CV_32F);
    img2temp.convertTo(img2temp, CV_32F);


    // 寻找凸包  hull1和hull2分别存储凸包的点
    vector<Point2f> hull1;
    vector<Point2f> hull2;
    vector<int> hullIndex;

    convexHull(points2, hullIndex, false, false);

    for (int i = 0; i < hullIndex.size(); i++)
    {
        hull1.push_back(points1[hullIndex[i]]);
        hull2.push_back(points2[hullIndex[i]]);
    }


    // 找到凸壳点的Delaunay三角剖分
    vector< vector<int> > dt;
    Rect rect(0, 0, img1Warped.cols, img1Warped.rows);
    calculateDelaunayTriangles(rect, hull2, dt);

    //在Delaunay三角形上的应用仿射变换
    //将图一的脸变到图二的对应位置   相当于在图一的复制图img1warped上的图二人脸区域有了一个图一人脸的变形
    for (size_t i = 0; i < dt.size(); i++)
    {
        vector<Point2f> t1, t2;
        // 得到图一图二上对应三角形的点
        for (size_t j = 0; j < 3; j++)
        {
            t1.push_back(points1[dt[i][j]]);
            t2.push_back(points2[dt[i][j]]);
        }

        warpTriangle(img1, img1Warped, t1, t2);
    }

    //两张人脸的融合比例
    img1Warped = 1 * img1Warped.clone() + 0 * img2temp.clone();


    // 计算mask（暂时不知道怎么翻译）
    //把图二的凸包的点存入hull8U（应该是一个8U的浮点型)
    vector<Point> hull8U;
    for (int i = 0; i < hull2.size(); i++)
    {
        Point pt(hull2[i].x, hull2[i].y);
        hull8U.push_back(pt);
    }

    Mat mask = Mat::zeros(img2.rows, img2.cols, img2.depth());
    fillConvexPoly(mask, &hull8U[0], hull8U.size(), Scalar(255, 255, 255));

    // 无缝克隆
    Rect r = boundingRect(hull2);
    //center为人脸二的中心点
    Point center = (r.tl() + r.br()) / 2;

    img1Warped.convertTo(img1Warped, CV_8UC3);
    seamlessClone(img1Warped, img2, mask, center, output, NORMAL_CLONE);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_administrator_try_1again_exchange_createpic(JNIEnv *env, jobject instance,
                                                             jobject bitmap) {

    // TODO
    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    AndroidBitmap_getInfo(env, bitmap, &info);
    void *pixels = NULL;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);
    Mat img(info.height, info.width, CV_8UC4, pixels);
    cvtColor(img, img, CV_BGRA2RGB);

    exchangepic = img.clone();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_administrator_try_1again_exchange_findface(JNIEnv *env, jobject instance) {

    // TODO
    Mat img = exchangepic.clone();
    vector<Rect> faces;
    Mat gray;
    cvtColor(img, gray, CV_BGR2GRAY);
    equalizeHist(gray, gray);
    ccf.detectMultiScale(gray, faces, 1.1, 3, 0, Size(150, 150), Size(1500, 1500));
    int num_face = faces.size();

    if(num_face <= 1)
        return num_face;
    else {
        dlib::cv_image<dlib::bgr_pixel> cimg(img);
        vector<dlib::rectangle> facesdlib;
        dlib::rectangle aface;
        for(int i = 0;i<faces.size();i++)
        {
            aface.set_top(faces[i].tl().y);
            aface.set_bottom(faces[i].br().y);
            aface.set_left(faces[i].tl().x);
            aface.set_right(faces[i].br().x);
            facesdlib.push_back(aface);
        }

        vector<dlib::full_object_detection> shapes;
        for(int i = 0;i<faces.size();i++)
            shapes.push_back(pose_model(cimg, facesdlib[i]));

        for(int i = 0;i<faces.size();i++)
        {
            vector<Point2f> points;
            for (int j = 0; j < 68; j++) {
                points.push_back(Point2f(shapes[i].part(j).x(), shapes[i].part(j).y()));
            }
            allpoints.push_back(points);
        }
    }

    return num_face;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_administrator_try_1again_exchange_faceexchange(JNIEnv *env, jobject instance,
                                                                jobject bitmap,jint num_face) {

    // TODO
    AndroidBitmapInfo info;
    memset(&info, 0, sizeof(info));
    AndroidBitmap_getInfo(env, bitmap, &info);
    void *pixels = NULL;
    AndroidBitmap_lockPixels(env, bitmap, &pixels);

    Mat output = exchangepic.clone();
    for (int i = 0; i < num_face; i++)
    {
        if (i + 1 == num_face)
            exchange(exchangepic, output, allpoints[i], allpoints[0], output);
        else
            exchange(exchangepic, output, allpoints[i], allpoints[i + 1], output);
    }
    exchangepic = output.clone();
    allpoints.clear();

    int a = 0, r1 = 0, g = 0, b = 0;
    for (int y = 0; y < info.height; ++y) {
        // From left to right
        for (int x = 0; x < info.width; ++x) {
            int *pixel = NULL;
            pixel = ((int *) pixels) + y * info.width + x;
            r1 = output.at<Vec3b>(y, x)[0];
            g = output.at<Vec3b>(y, x)[1];
            b = output.at<Vec3b>(y, x)[2];
            a = RGBA_A(*pixel);
            *pixel = MAKE_RGBA(r1, g, b, a);
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
}

