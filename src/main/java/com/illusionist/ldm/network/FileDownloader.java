package com.illusionist.ldm.network;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.*;
import java.security.InvalidParameterException;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@SuppressWarnings("unused")
public class FileDownloader {
    //region Constants
    public static final int PAUSED = 0;
    public static final int RUNNING = 1;
    public static final int COMPLETE = 2;
    public static final int STOPPED = 3;
    public static final int ERROR = 4;

    private static final int[] REDIRECT_RESPONSES = { 301, 302, 307, 308 };
    //endregion

    //region Data
    private String downloadUrl;
    private String downloadFilepath;

    private Object userData;
    //endregion

    //region States
    private Future<Void> downloadTask = null;
    private Future<HttpResponse<InputStream>> downloadResponse = null;

    private final AtomicInteger downloadState = new AtomicInteger(0);

    private long contentSize = 0;
    private boolean resumed = false;
    //endregion

    //region Callbacks
    private ActionListener onDownloadPaused = null;
    private ActionListener onDownloadRunning = null;
    private ActionListener onDownloadStopped = null;
    private ActionListener onDownloadCompleted = null;
    private ActionListener onDownloadError = null;
    private DataReceiveListener onDataReceive = null;
    //endregion

    private static final HttpClient client = HttpClient.newBuilder().build();

    public FileDownloader() {
        downloadState.set(PAUSED);
    }

    //region Operations
    public void start() {
        // We are trying to restart something in progress
        if(downloadTask != null
                && downloadTask.state() == Future.State.RUNNING
                && downloadState.get() != PAUSED)
            return;

        if(downloadUrl.isBlank())
            return;

        if(downloadFilepath.isBlank())
            return;

        if(downloadTask == null || downloadTask.state() != Future.State.RUNNING) {
            resumed = false;
            downloadTask = CompletableFuture.runAsync(this::download);
        }
        else {
            resumed = true;
            setDownloadState(RUNNING);
        }
    }

    public void stop() {
        if(downloadState.get() == RUNNING || downloadState.get() == PAUSED) {
            setDownloadState(STOPPED);
        }
    }

    public void pause() {
        if(downloadState.get() == RUNNING) {
            setDownloadState(PAUSED);
        }
    }
    //endregion

    //region Set/Get Path Information
    public void setDownloadUrl(String url) {
        downloadUrl = url;
    }

    public final String getDownloadUrl() {
        return downloadUrl;
    }

    public void setFilePath(String filepath) {
        downloadFilepath = filepath;
    }

    public final String getFilePath() {
        return downloadFilepath;
    }

    public void setUserData(Object userData) {
        this.userData = userData;
    }

    public final Object getUserData() {
        return userData;
    }

    public final boolean getResumed() {
        return resumed;
    }
    //endregion

    //region State change
    private void setDownloadState(int state) {
        downloadState.set(state);
        onDownloadStateChanged(state);
    }

    public int getDownloadStatus() {
        return downloadState.get();
    }

    private void onDownloadStateChanged(int state) throws InvalidParameterException {
        switch (state)
        {
            case PAUSED:
                onPauseDownload();
                break;
            case RUNNING:
                onStartDownload();
                break;
            case COMPLETE:
                onCompleteDownload();
                break;
            case STOPPED:
                onStopDownload();
                break;
            case ERROR:
                onErrorDownload();
                break;
            default:
                throw new InvalidParameterException("Parameter: state was set to an invalid value.");
        }
    }
    //endregion

    //region Download functions
    private void clearState() {
        downloadResponse = null;
        downloadTask = null;

        contentSize = 0;

        resumed = false;

        File fileObject = new File(downloadFilepath);

        if(fileObject.isFile() && fileObject.delete()) {
        }
    }

    private boolean isValidResponse() throws URISyntaxException, ExecutionException, InterruptedException {
        boolean result = false;

        var ref = new Object() {
            int statusCode = downloadResponse.get().statusCode();
        };

        // Keep connecting while we have matching redirects
        while(IntStream.of(REDIRECT_RESPONSES).anyMatch(x -> x == ref.statusCode)) {

            HttpHeaders headers = downloadResponse.get().headers();
            
            if(headers.firstValue("Location").isEmpty())
                break;
            
            String newAddress = headers.firstValue("Location").get();
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(newAddress)).build();

            downloadResponse = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());

            ref.statusCode = downloadResponse.get().statusCode();
        }

        // if we succeeded
        if(ref.statusCode == 200) {
            result = true;
        }

        return result;
    }

    private boolean open() {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(new URI(downloadUrl)).build();

            downloadResponse = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());

            if (isValidResponse()) {
                // A connection is valid even if there is no Content-Length
                // which is why this was moved out of isValidResponse.
                OptionalLong dataLength = downloadResponse.get().headers().firstValueAsLong("Content-Length");

                if(dataLength.isEmpty()) {
                    contentSize = 0;
                } else {
                    contentSize = dataLength.getAsLong();
                }
            } else {
                clearState();
                return false;
            }
        } catch (ExecutionException | InterruptedException | URISyntaxException e) {
            return false;
        }

        return true;
    }

    private void download() {
        try {
            if (!open()) {
                clearState();
                setDownloadState(ERROR);
                return;
            }

            // Get out stream object
            InputStream stream = downloadResponse.get().body();

            long totalBytesRead = 0;

            try (FileOutputStream fileOut = new FileOutputStream(downloadFilepath)) {

                // Technically where we know the download has actually started
                setDownloadState(RUNNING);

                int bytesRead;
                byte[] buffer = new byte[8192];

                while (true) {
                    // Read all the available bytes from the stream
                    try {
                        bytesRead = stream.read(buffer);
                    } catch (IOException e) {
                        if (open()) {
                            stream = downloadResponse.get().body();
                            stream.skipNBytes(totalBytesRead);
                            continue;
                        } else {
                            // Failed to reopen stream
                            clearState();
                            setDownloadState(ERROR);
                            return;
                        }
                    }

                    // The value -1 is not really an error so break out
                    if (bytesRead == -1)
                        break;

                    // Write data to file
                    fileOut.write(buffer, 0, bytesRead);

                    // Use the total bytes read not what was available
                    totalBytesRead += bytesRead;

                    // Create another request state?
                    if (onDataReceive != null && downloadState.get() == RUNNING)
                        onDataReceive.onDataReceive(this, totalBytesRead, contentSize);

                    if (downloadState.get() == PAUSED) {
                        while (downloadState.get() == PAUSED) {
                            Thread.sleep(100);
                        }
                    }

                    // We should be doing clean up delete task and request
                    if (downloadState.get() == STOPPED) {
                        // The filestream object needs to be closed first
                        fileOut.close();
                        clearState();
                        return;
                    }
                }

                fileOut.flush();
            }

            stream.close();

            // We finished success
            setDownloadState(COMPLETE);
        } catch (InterruptedException | ExecutionException | IOException e) {
            clearState();
            setDownloadState(ERROR);
        }
    }
    //endregion

    //region State change callbacks
    private void onStartDownload() {
        if(onDownloadRunning != null)
            onDownloadRunning.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "start"));
    }

    private void onStopDownload() {
        if(onDownloadStopped != null)
            onDownloadStopped.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "stop"));
    }

    private void onPauseDownload() {
        if(onDownloadPaused != null)
            onDownloadPaused.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "pause"));
    }

    private void onCompleteDownload() {
        if(onDownloadCompleted != null)
            onDownloadCompleted.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "complete"));
    }

    private void onErrorDownload() {
        if(onDownloadError != null)
            onDownloadError.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "error"));
    }
    //endregion

    //region ActionListener Setters
    public void setOnDownloadPaused(ActionListener onDownloadPaused) {
        this.onDownloadPaused = onDownloadPaused;
    }

    public void setOnDownloadStopped(ActionListener onDownloadStopped) {
        this.onDownloadStopped = onDownloadStopped;
    }

    public void setOnDownloadRunning(ActionListener onDownloadRunning) {
        this.onDownloadRunning = onDownloadRunning;
    }

    public void setOnDownloadCompleted(ActionListener onDownloadCompleted) {
        this.onDownloadCompleted = onDownloadCompleted;
    }

    public void setOnDownloadError(ActionListener onDownloadError) {
        this.onDownloadError = onDownloadError;
    }

    public void setOnDataRecv(DataReceiveListener onDataReceive) {
        this.onDataReceive = onDataReceive;
    }
    //endregion
}
