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

package org.photonvision.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.photonvision.common.configuration.ConfigManager;
import org.photonvision.common.configuration.NetworkConfig;
import org.photonvision.common.dataflow.DataChangeDestination;
import org.photonvision.common.dataflow.DataChangeService;
import org.photonvision.common.dataflow.events.IncomingWebSocketEvent;
import org.photonvision.common.dataflow.networktables.NetworkTablesManager;
import org.photonvision.common.hardware.HardwareManager;
import org.photonvision.common.hardware.Platform;
import org.photonvision.common.logging.LogGroup;
import org.photonvision.common.logging.Logger;
import org.photonvision.common.networking.NetworkManager;
import org.photonvision.common.util.ShellExec;
import org.photonvision.common.util.TimedTaskManager;
import org.photonvision.common.util.file.ProgramDirectoryUtilities;
import org.photonvision.vision.calibration.CameraCalibrationCoefficients;
import org.photonvision.vision.processes.VisionModule;
import org.photonvision.vision.processes.VisionModuleManager;

public class RequestHandler {
    // Treat all 2XX calls as "INFO"
    // Treat all 4XX calls as "ERROR"
    // Treat all 5XX calls as "ERROR"
    private static final Logger logger = new Logger(RequestHandler.class, LogGroup.WebServer);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static Optional<UploadedFile> verifyUploadedFile(
            final UploadedFile file,
            final Context ctx,
            final String expectedType
    ) {
        if (file == null) {
            ctx.status(400);
            ctx.result(
                    "No File was sent with the request. Make sure that the hardware config json is sent at the key 'data'");
            logger.error(
                    "No File was sent with the request. Make sure that the hardware config json is sent at the key 'data'");
            return Optional.empty();
        } else if (!file.extension().contains(expectedType)) {
            ctx.status(400);
            ctx.result(
                    "The uploaded file was not of type 'json'. The uploaded file should be a .json file.");
            logger.error(
                    "The uploaded file was not of type 'json'. The uploaded file should be a .json file.");
            return Optional.empty();
        }

        return Optional.of(file);
    }

    @SuppressWarnings("SameParameterValue")
    private static Optional<File> getUploadedFileAsFile(
            final UploadedFile file,
            final Context ctx,
            final String expectedType
    ) {
        final Optional<UploadedFile> uploadedFile = verifyUploadedFile(file, ctx, expectedType);
        if (uploadedFile.isEmpty()) {
            return Optional.empty();
        }

        final Optional<File> tempFilePath = handleTempFileCreation(uploadedFile.get());
        if (tempFilePath.isEmpty()) {
            ctx.status(500);
            ctx.result("There was an error while creating a temporary copy of the file");
            logger.error("There was an error while creating a temporary copy of the file");
        }

        return tempFilePath;
    }

    public static void onSettingsImportRequest(Context ctx) {
        var file = ctx.uploadedFile("data");

        if (file == null) {
            ctx.status(400);
            ctx.result(
                    "No File was sent with the request. Make sure that the settings zip is sent at the key 'data'");
            logger.error(
                    "No File was sent with the request. Make sure that the settings zip is sent at the key 'data'");
            return;
        }

        if (!file.extension().contains("zip")) {
            ctx.status(400);
            ctx.result(
                    "The uploaded file was not of type 'zip'. The uploaded file should be a .zip file.");
            logger.error(
                    "The uploaded file was not of type 'zip'. The uploaded file should be a .zip file.");
            return;
        }

        // Create a temp file
        var tempFilePath = handleTempFileCreation(file);

        if (tempFilePath.isEmpty()) {
            ctx.status(500);
            ctx.result("There was an error while creating a temporary copy of the file");
            logger.error("There was an error while creating a temporary copy of the file");
            return;
        }

        if (ConfigManager.saveUploadedSettingsZip(tempFilePath.get())) {
            ctx.status(200);
            ctx.result("Successfully saved the uploaded settings zip");
            logger.info("Successfully saved the uploaded settings zip");
        } else {
            ctx.status(500);
            ctx.result("There was an error while saving the uploaded zip file");
            logger.error("There was an error while saving the uploaded zip file");
        }
    }

    public static void onSettingsExportRequest(Context ctx) {
        logger.info("Exporting Settings to ZIP Archive");

        try {
            var zip = ConfigManager.getInstance().getSettingsFolderAsZip();
            var stream = new FileInputStream(zip);
            logger.info("Uploading settings with size " + stream.available());

            ctx.contentType("application/zip");
            ctx.header(
                    "Content-Disposition", "attachment; filename=\"photonvision-settings-export.zip\"");

            ctx.result(stream);
            ctx.status(200);
        } catch (IOException e) {
            logger.error("Unable to export settings archive, bad recode from zip to byte");
            ctx.status(500);
            ctx.result("There was an error while exporting the settings archive");
        }
    }

    public static void onHardwareConfigRequest(Context ctx) {
        final Optional<File> tempFilePath =
                getUploadedFileAsFile(ctx.uploadedFile("data"), ctx, "json");
        if (tempFilePath.isEmpty()) {
            return;
        }

        if (ConfigManager.getInstance().saveUploadedHardwareConfig(tempFilePath.get().toPath())) {
            ctx.status(200);
            ctx.result("Successfully saved the uploaded hardware config");
            logger.info("Successfully saved the uploaded hardware config");
        } else {
            ctx.status(500);
            ctx.result("There was an error while saving the uploaded hardware config");
            logger.error("There was an error while saving the uploaded hardware config");
        }
    }

    public static void onHardwareSettingsRequest(Context ctx) {
        final Optional<File> tempFilePath =
                getUploadedFileAsFile(ctx.uploadedFile("data"), ctx, "json");
        if (tempFilePath.isEmpty()) {
            return;
        }

        if (ConfigManager.getInstance().saveUploadedHardwareSettings(tempFilePath.get().toPath())) {
            ctx.status(200);
            ctx.result("Successfully saved the uploaded hardware settings");
            logger.info("Successfully saved the uploaded hardware settings");
        } else {
            ctx.status(500);
            ctx.result("There was an error while saving the uploaded hardware settings");
            logger.error("There was an error while saving the uploaded hardware settings");
        }
    }

    public static void onNetworkConfigRequest(Context ctx) {
        final Optional<File> tempFilePath =
                getUploadedFileAsFile(ctx.uploadedFile("data"), ctx, "json");
        if (tempFilePath.isEmpty()) {
            return;
        }

        if (ConfigManager.getInstance().saveUploadedNetworkConfig(tempFilePath.get().toPath())) {
            ctx.status(200);
            ctx.result("Successfully saved the uploaded network config");
            logger.info("Successfully saved the uploaded network config");
        } else {
            ctx.status(500);
            ctx.result("There was an error while saving the uploaded network config");
            logger.error("There was an error while saving the uploaded network config");
        }
    }

    public static void onOfflineUpdateRequest(Context ctx) {
        final Optional<UploadedFile> file =
                verifyUploadedFile(ctx.uploadedFile("jarData"), ctx, "jar");
        if (file.isEmpty()) {
            return;
        }

        try {
            final Optional<String> programDirectory = ProgramDirectoryUtilities.getProgramDirectory();
            final Path filePath = Paths.get(
                    programDirectory.orElseThrow(
                            () -> new FileNotFoundException("getProgramDirectory() returned empty program directory!")
                    ),
                    "photonvision.jar"
            );


            final File targetFile = new File(filePath.toString());
            try (
                    final InputStream fileContent = file.get().content();
                    final FileOutputStream stream = new FileOutputStream(targetFile)
            ) {
                fileContent.transferTo(stream);
            } finally {
                ctx.status(200);
                ctx.result(
                        "Offline update successfully complete. PhotonVision will restart in the background.");
                logger.info(
                        "Offline update successfully complete. PhotonVision will restart in the background.");
                restartProgram();
            }
        } catch (final FileNotFoundException e) {
            ctx.result("The current program jar file couldn't be found.");
            ctx.status(500);
            logger.error("The current program jar file couldn't be found.", e);
        } catch (final IOException e) {
            ctx.result("Unable to overwrite the existing program with the new program.");
            ctx.status(500);
            logger.error("Unable to overwrite the existing program with the new program.", e);
        }
    }

    public static void onGeneralSettingsRequest(Context ctx) {
        NetworkConfig config;
        try {
            config = OBJECT_MAPPER.readValue(ctx.body(), NetworkConfig.class);

            ctx.status(200);
            ctx.result("Successfully saved general settings");
            logger.info("Successfully saved general settings");
        } catch (JsonProcessingException e) {
            // If the settings can't be parsed, use the default network settings
            config = new NetworkConfig();

            ctx.status(400);
            ctx.result("The provided general settings were malformed");
            logger.error("The provided general settings were malformed", e);
        }

        ConfigManager.getInstance().setNetworkSettings(config);
        ConfigManager.getInstance().requestSave();

        NetworkManager.getInstance().reinitialize();

        NetworkTablesManager.getInstance().setConfig(config);
    }

    public static void onCameraSettingsRequest(Context ctx) {
        try {
            final JsonNode data = OBJECT_MAPPER.readTree(ctx.body());

            final int index = data.get("index").asInt();
            final double fov = data.get("settings").get("fov").asDouble();

            final VisionModule module = VisionModuleManager.getInstance().getModule(index);

            module.setFov(fov);
            module.saveModule();

            ctx.status(200);
            ctx.result("Successfully saved camera settings");
            logger.info("Successfully saved camera settings");
        } catch (JsonProcessingException | NullPointerException e) {
            ctx.status(400);
            ctx.result("The provided camera settings were malformed");
            logger.error("The provided camera settings were malformed", e);
        }
    }

    public static void onLogExportRequest(Context ctx) {
        if (!Platform.isLinux()) {
            ctx.status(405);
            ctx.result("Logs can only be exported on a Linux platform");
            // INFO only log because this isn't ERROR worthy
            logger.info("Logs can only be exported on a Linux platform");
            return;
        }

        try {
            ShellExec shell = new ShellExec();
            var tempPath = Files.createTempFile("photonvision-journalctl", ".txt");
            shell.executeBashCommand("journalctl -u photonvision.service > " + tempPath.toAbsolutePath());

            // what the hell is going on here
            //noinspection StatementWithEmptyBody
            while (!shell.isOutputCompleted()) {
                // TODO: add timeout
            }

            if (shell.getExitCode() == 0) {
                // Wrote to the temp file! Add it to the ctx
                var stream = new FileInputStream(tempPath.toFile());
                ctx.contentType("text/plain");
                ctx.header("Content-Disposition", "attachment; filename=\"photonvision-journalctl.txt\"");
                ctx.status(200);
                ctx.result(stream);
                logger.info("Uploading settings with size " + stream.available());
            } else {
                ctx.status(500);
                ctx.result("The journalctl service was unable to export logs");
                logger.error("The journalctl service was unable to export logs");
            }
        } catch (final IOException ioException) {
            ctx.status(500);
            ctx.result("There was an error while exporting journactl logs");
            logger.error("There was an error while exporting journactl logs", ioException);
        }
    }

    public static void onCalibrationEndRequest(Context ctx) {
        logger.info("Calibrating camera! This will take a long time...");

        try {
            final int index = OBJECT_MAPPER.readTree(ctx.body()).get("index").asInt();
            final CameraCalibrationCoefficients calData =
                    VisionModuleManager.getInstance().getModule(index).endCalibration();

            if (calData == null) {
                ctx.result("The calibration process failed");
                ctx.status(500);
                logger.error(
                        "The calibration process failed. Calibration data for module at index ("
                                + index
                                + ") was null");
                return;
            }

            ctx.result("Camera calibration successfully completed!");
            ctx.status(200);
            logger.info("Camera calibration successfully completed!");
        } catch (final JsonProcessingException jsonProcessingException) {
            ctx.status(400);
            ctx.result(
                    "The 'index' field was not found in the request. Please make sure the index of the vision module is specified with the 'index' key.");
            logger.error(
                    "The 'index' field was not found in the request. Please make sure the index of the vision module is specified with the 'index' key.",
                    jsonProcessingException);
        } catch (final Exception exception) {
            ctx.status(500);
            ctx.result("There was an error while ending calibration");
            logger.error("There was an error while ending calibration", exception);
        }
    }

    public static void onCalibrationImportRequest(final Context ctx) {
        final String data = ctx.body();

        try {
            final JsonNode actualObj = OBJECT_MAPPER.readTree(data);

            final int cameraIndex = actualObj.get("cameraIndex").asInt();
            final JsonNode payload = OBJECT_MAPPER.readTree(actualObj.get("payload").asText());
            final CameraCalibrationCoefficients coefficients =
                    CameraCalibrationCoefficients.parseFromCalibdbJson(payload);

            final IncomingWebSocketEvent<CameraCalibrationCoefficients> uploadCalibrationEvent =
                    new IncomingWebSocketEvent<>(
                            DataChangeDestination.DCD_ACTIVE_MODULE,
                            "calibrationUploaded",
                            coefficients,
                            cameraIndex,
                            null
                    );
            DataChangeService.getInstance().publishEvent(uploadCalibrationEvent);

            ctx.status(200);
            ctx.result("Calibration imported successfully from CalibDB data!");
            logger.info("Calibration imported successfully from CalibDB data!");
        } catch (final JsonProcessingException jsonProcessingException) {
            ctx.status(400);
            ctx.result(
                    "The Provided CalibDB data is malformed and cannot be parsed for the required fields.");
            logger.error(
                    "The Provided CalibDB data is malformed and cannot be parsed for the required fields.",
                    jsonProcessingException
            );
        }
    }

    public static void onProgramRestartRequest(Context ctx) {
        // TODO, check if this was successful or not
        ctx.status(204);
        restartProgram();
    }

    public static void onDeviceRestartRequest(Context ctx) {
        ctx.status(HardwareManager.getInstance().restartDevice() ? 204 : 500);
    }

    public static void onCameraNicknameChangeRequest(final Context ctx) {
        try {
            final JsonNode data = OBJECT_MAPPER.readTree(ctx.body());

            final String name = data.get("name").asText();
            final int idx = data.get("cameraIndex").asInt();

            VisionModuleManager.getInstance().getModule(idx).setCameraNickname(name);

            ctx.status(200);
            ctx.result("Successfully changed the camera name to: " + name);
            logger.info("Successfully changed the camera name to: " + name);
        } catch (final JsonProcessingException jsonProcessingException) {
            ctx.status(400);
            ctx.result("The provided nickname data was malformed");
            logger.error("The provided nickname data was malformed", jsonProcessingException);
        } catch (final Exception exception) {
            ctx.status(500);
            ctx.result("An error occurred while changing the camera's nickname");
            logger.error("An error occurred while changing the camera's nickname", exception);
        }
    }

    public static void onMetricsPublishRequest(final Context ctx) {
        HardwareManager.getInstance().publishMetrics();
        ctx.status(204);
    }

    /**
     * Create a temporary file using the UploadedFile from Javalin.
     *
     * @param file the uploaded file.
     * @return Temporary file. Empty if the temporary file was unable to be created.
     */
    private static Optional<File> handleTempFileCreation(final UploadedFile file) {
        final File tempFilePath =
                new File(Path.of(System.getProperty("java.io.tmpdir"), file.filename()).toString());
        final boolean makeDirsRes = tempFilePath.getParentFile().mkdirs();

        if (!makeDirsRes) {
            logger.error(
                    "There was an error while uploading " + file.filename() + " to the temp folder!");
            return Optional.empty();
        }

        try {
            FileUtils.copyInputStreamToFile(file.content(), tempFilePath);
        } catch (IOException e) {
            logger.error(
                    "There was an error while uploading " + file.filename() + " to the temp folder!");
            return Optional.empty();
        }

        return Optional.of(tempFilePath);
    }

    /**
     * Restart the running program. Note that this doesn't actually restart the program itself,
     * instead, it relies on systemd or an equivalent.
     */
    private static void restartProgram() {
        TimedTaskManager.getInstance()
                .addOneShotTask(
                        () -> {
                            if (Platform.isLinux()) {
                                try {
                                    new ShellExec().executeBashCommand("systemctl restart photonvision.service");
                                } catch (final IOException ioException) {
                                    logger.error("Could not restart device!", ioException);
                                    System.exit(0);
                                }
                            } else {
                                System.exit(0);
                            }
                        },
                        0
                );
    }
}
