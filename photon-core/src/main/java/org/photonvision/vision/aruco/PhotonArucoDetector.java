/*
 * Copyright (C) Photon Vision.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.photonvision.vision.aruco;

import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.util.Units;
import org.opencv.aruco.Aruco;
import org.opencv.core.Mat;
import org.opencv.objdetect.ArucoDetector;
import org.photonvision.common.logging.LogGroup;
import org.photonvision.common.logging.Logger;
import org.photonvision.vision.calibration.CameraCalibrationCoefficients;

import java.util.ArrayList;

public class PhotonArucoDetector {
    private static final Logger logger = new Logger(PhotonArucoDetector.class, LogGroup.VisionModule);

    private static final Rotation3d ARUCO_BASE_ROTATION =
            new Rotation3d(VecBuilder.fill(0, 0, 1), Units.degreesToRadians(180));

    private final Mat ids;

    private final Mat tvecs;
    private final Mat rvecs;
    private final ArrayList<Mat> corners;

    public PhotonArucoDetector() {
        logger.debug("New Aruco Detector");

        this.ids = new Mat();
        this.tvecs = new Mat();
        this.rvecs = new Mat();
        this.corners = new ArrayList<>();
    }

    public ArucoDetectionResult[] detect(
            Mat grayscaleImg,
            float tagSize,
            CameraCalibrationCoefficients coeffs,
            ArucoDetector detector) {
        detector.detectMarkers(grayscaleImg, corners, ids);
        if (coeffs != null) {
            //noinspection deprecation
            Aruco.estimatePoseSingleMarkers(
                    corners,
                    tagSize,
                    coeffs.getCameraIntrinsicsMat(),
                    coeffs.getDistCoeffsMat(),
                    rvecs,
                    tvecs);
        }

        ArucoDetectionResult[] toReturn = new ArucoDetectionResult[corners.size()];
        for (int i = 0; i < corners.size(); i++) {
            Mat cornerMat = corners.get(i);
            // logger.debug(cornerMat.dump());
            double[] xCorners = new double[]{
                    cornerMat.get(0, 0)[0],
                    cornerMat.get(0, 1)[0],
                    cornerMat.get(0, 2)[0],
                    cornerMat.get(0, 3)[0]
            };
            double[] yCorners = new double[]{
                    cornerMat.get(0, 0)[1],
                    cornerMat.get(0, 1)[1],
                    cornerMat.get(0, 2)[1],
                    cornerMat.get(0, 3)[1]
            };
            cornerMat.release();

            double[] tvec;
            double[] rvec;
            if (coeffs != null) {
                // Need to apply a 180 rotation about Z
                var origRvec = rvecs.get(i, 0);
                var axisangle = VecBuilder.fill(origRvec[0], origRvec[1], origRvec[2]);
                Rotation3d rotation = new Rotation3d(axisangle, axisangle.normF());
                var ocvRotation = ARUCO_BASE_ROTATION.rotateBy(rotation);

                var angle = ocvRotation.getAngle();
                var finalAxisAngle = ocvRotation.getAxis().times(angle);

                tvec = tvecs.get(i, 0);
                rvec = finalAxisAngle.getData();
            } else {
                tvec = new double[] {0, 0, 0};
                rvec = new double[] {0, 0, 0};
            }

            toReturn[i] =
                    new ArucoDetectionResult(xCorners, yCorners, (int) ids.get(i, 0)[0], tvec, rvec);
        }
        rvecs.release();
        tvecs.release();
        ids.release();

        return toReturn;
    }
}
