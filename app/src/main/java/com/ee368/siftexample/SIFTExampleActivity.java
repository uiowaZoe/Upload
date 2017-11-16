package com.ee368.siftexample;

//********************************************************************************************
//EE368 Digital Image Processing
//Android Tutorial #3: Server-Client Communication
//Author: Derek Pang (dcypang@stanford.edu), David Chen (dmchen@stanford.edu)
//********************************************************************************************/

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;


import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

public class SIFTExampleActivity extends Activity {

    private static final String TAG = "SIFTExampleActivity";

    Preview mPreview;
    ResultView mResultView;
    private Context mContext = this;

    /** PLEASE PUT YOUR SERVER URL **/
    private final String SERVERURL = "http://172.17.80.39:8000";

    private final static String INPUT_IMG_FILENAME = "/temp.jpeg"; //name for storing image captured by camera view

    //flag to check if camera is ready for capture
    private boolean mCameraReadyFlag = true;

    // Called when the activity is first created.
    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        takePicture();
    }
    private void takePicture() {
        Intent intentFromCapture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intentFromCapture.putExtra(MediaStore.EXTRA_OUTPUT, getFileUri(new File(Environment.getExternalStorageDirectory().toString()+"/", "temp.jpeg"),this));
        startActivityForResult(intentFromCapture, 100);
    }
    public static Uri getFileUri(File file, Context context){
        return Uri.fromFile(file);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case 100:
                //File tempFile = new File(Environment.getExternalStorageDirectory().toString()+"/temp.jpeg");
                ServerTask task = new ServerTask();
                task.execute(Environment.getExternalStorageDirectory().toString()+"/temp.jpeg");
                break;
        }
    }
    //*******************************************************************************
    //Push image processing task to server
    //*******************************************************************************

    public class ServerTask  extends AsyncTask<String, Integer , Void>
    {
        //Task state
        private final int UPLOADING_PHOTO_STATE  = 0;
        private final int SERVER_PROC_STATE  = 1;

        private ProgressDialog dialog;

        //upload photo to server
        HttpURLConnection uploadPhoto(FileInputStream fileInputStream)
        {
            final String serverFileName = "test"+ (int) Math.round(Math.random()*1000) + ".jpg";
            final String lineEnd = "\r\n";
            final String twoHyphens = "--";
            final String boundary = "*****";

            try
            {
                URL url = new URL(SERVERURL);
                // Open a HTTP connection to the URL
                final HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                // Allow Inputs
                conn.setDoInput(true);
                // Allow Outputs
                conn.setDoOutput(true);
                // Don't use a cached copy.
                conn.setUseCaches(false);

                // Use a post method.
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);

                DataOutputStream dos = new DataOutputStream( conn.getOutputStream() );

                dos.writeBytes(twoHyphens + boundary + lineEnd);

                dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + serverFileName +"\"" + lineEnd);
                dos.writeBytes(lineEnd);
                dos.writeBytes("Content-Type multipart/form-data;boundary"+boundary+lineEnd);


                // create a buffer of maximum size
                int bytesAvailable = fileInputStream.available();
                int maxBufferSize = 1024;
                int bufferSize = Math.min(bytesAvailable, maxBufferSize);
                byte[] buffer = new byte[bufferSize];

                // read file and write it into form...
                int bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                while (bytesRead > 0)
                {
                    dos.write(buffer, 0, bufferSize);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                }

                // send multipart form data after file data...
                dos.writeBytes(lineEnd);
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                publishProgress(SERVER_PROC_STATE);
                // close streams
                fileInputStream.close();
                dos.flush();

                return conn;
            }
            catch (MalformedURLException ex){
                Log.e(TAG, "error: " + ex.getMessage(), ex);
                return null;
            }
            catch (IOException ioe){
                Log.e(TAG, "error: " + ioe.getMessage(), ioe);
                return null;
            }
        }

        //get image result from server and display it in result view
        void getResultImage(HttpURLConnection conn){
            // retrieve the response from server
            InputStream is;
            try {
                is = conn.getInputStream();
                //get result image from server
                if(is.available()>0){
                    Log.i("TAG","InputStrem >0");
                    mResultView.resultImage = BitmapFactory.decodeStream(is);
                    is.close();
                    mResultView.IsShowingResult = true;
                }

            } catch (IOException e) {
                Log.e(TAG,e.toString());
                e.printStackTrace();
            }
        }

        //Main code for processing image algorithm on the server

        void processImage(String inputImageFilePath){
            publishProgress(UPLOADING_PHOTO_STATE);
            File inputFile = new File(inputImageFilePath);
            try {

                //create file stream for captured image file
                FileInputStream fileInputStream  = new FileInputStream(inputFile);

                //upload photo
                final HttpURLConnection  conn = uploadPhoto(fileInputStream);

                //get processed photo from server
                if (conn != null){
                    getResultImage(conn);}
                fileInputStream.close();
            }
            catch (FileNotFoundException ex){
                Log.e(TAG, ex.toString());
            }
            catch (IOException ex){
                Log.e(TAG, ex.toString());
            }
        }

        public ServerTask() {
            dialog = new ProgressDialog(mContext);
        }

        protected void onPreExecute() {
            this.dialog.setMessage("Photo captured");
            this.dialog.show();
        }
        @Override
        protected Void doInBackground(String... params) {			//background operation
            String uploadFilePath = params[0];
            //Log.i("PATH",uploadFilePath);
            processImage(uploadFilePath);
            //release camera when previous image is processed
            mCameraReadyFlag = true;
            return null;
        }
        //progress update, display dialogs
        @Override
        protected void onProgressUpdate(Integer... progress) {
            Log.i(TAG,"onProgress Update");
            if(progress[0] == UPLOADING_PHOTO_STATE){
                dialog.setMessage("Uploading");
                dialog.show();
            }
            else if (progress[0] == SERVER_PROC_STATE){
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
                dialog.setMessage("Processing");
                dialog.show();
            }
        }
        @Override
        protected void onPostExecute(Void param) {
            Log.i("TAG","Post Execute");
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }
}