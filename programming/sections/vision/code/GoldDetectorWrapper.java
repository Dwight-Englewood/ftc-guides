public class GoldDetectorWrapper extends OpenCVPipeline implements Subsystem {

    public static final int minContourArea = 1000;
    public static final int maxContourArea = 5000;
    public ImageView imageView = ImageView.THRESH;
    public GoldDetectorPipeline grip = new GoldDetectorPipeline();
    // this is just here so we can expose it later thru getContours.
    public List<MatOfPoint> contours = new ArrayList<>();
    // To keep it such that we don't have to instantiate a new Mat every call to processFrame,
    // we declare the Mats upSafe here and reuse them. This is easier on the garbage collector.
    private Mat hsv = new Mat();
    private Mat thresholded = new Mat();
    private MineralPosition currentState;

    public synchronized List<MatOfPoint> getContours() {
        return contours;
    }

    // This is called every camera frame.
    @Override
    public Mat processFrame(Mat rgba, Mat gray) {
        grip.process(rgba);
        contours = grip.findContoursOutput();
        switch (imageView) {
            case RESIZE:
                return grip.resizeImageOutput();
            case HSV:
                return grip.cvCvtcolorOutput();
            case BLUR:
                return grip.blurOutput();
            case THRESH:
                return grip.hsvThresholdOutput();
            case CONTOUR:
                Mat contourImageOutput = grip.resizeImageOutput().clone();
                Imgproc.drawContours(contourImageOutput, grip.findContoursOutput(), -1, new Scalar(255, 255, 255), 8);
                return contourImageOutput;
            default:
                return rgba;
        }

    }

    @Override
    public void init(HardwareMap hwMap) {
        this.init(hwMap.appContext, CameraViewDisplay.getInstance());
    }

    @Override
    public void start() {
        this.enable();
    }

    @Override
    public void reset() {

    }

    @Override
    public void stop() {
        this.disable();

    }

    private void updateState() {
        try {
        List<MatOfPoint> contours = this.getContours();
        for (int i = 0; i < contours.size(); i++) {
            Rect boundingRect = Imgproc.boundingRect(contours.get(i));
            if (boundingRect.area() < 50) {
                contours.remove(i);
                i--;
            }
        }


        if (contours.size() == 0) {
            this.currentState = MineralPosition.RIGHT;
        } else if (contours.size() >= 1) {
            double areaSum = 0;
            double xAvg = 0;
            //double yAvg = 0;
            for (int i = 0; i < contours.size(); i++) {
                Rect boundingRect = Imgproc.boundingRect(contours.get(i));
                double areaLol = boundingRect.area();
                areaSum = areaSum + areaLol;
                xAvg = xAvg + areaLol * (boundingRect.x + boundingRect.width / 2d);
                //yAvg = yAvg + areaLol*(boundingRect.y + boundingRect.height / 2d);
            }
            xAvg = xAvg / areaSum;
            //yAvg = yAvg / areaSum;
            //telemetry.addData("x", boundingRect.x + boundingRect.width / 2);
            //telemetry.addData("y", boundingRect.y + boundingRect.height / 2);
            if (xAvg > 160) {
                this.currentState = MineralPosition.CENTER;
            } else {
                this.currentState = MineralPosition.LEFT;
            }

        } else {
            this.currentState = MineralPosition.NOTVISIBLE;
        }} catch (IndexOutOfBoundsException e) {

        }
    }

    @Override
    public State getState() {
        this.updateState();
        return this.currentState;
    }

    public List<MatOfPoint> filterContours(List<MatOfPoint> contours) {
        for (int i = 0; i < contours.size(); i++) {
            Rect boundingRect = Imgproc.boundingRect(contours.get(0));
            if (!(boundingRect.area() > minContourArea) || !(boundingRect.area() < maxContourArea)) {
                contours.remove(i);
                i--;
            }
        }

        return contours;

    }

    public enum ImageView {
        RESIZE, HSV, BLUR, THRESH, CONTOUR, DEFAULT;
    }
}
