/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.groceryhelper.localize;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;

import android.media.ImageReader.OnImageAvailableListener;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;


import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import com.example.groceryhelper.R;
import com.example.groceryhelper.localize.customview.OverlayView;
import com.example.groceryhelper.localize.customview.OverlayView.DrawCallback;
import com.example.groceryhelper.localize.env.BorderedText;
import com.example.groceryhelper.localize.env.ImageUtils;
import com.example.groceryhelper.localize.env.Logger;
import com.example.groceryhelper.localize.tflite.Classifier;
import com.example.groceryhelper.localize.tflite.TFLiteObjectDetectionAPIModel;
import com.example.groceryhelper.localize.tflite.TFLiteObjectDetectionEfficientDet;
import com.example.groceryhelper.localize.tracking.MultiBoxTracker;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.FaceAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

import com.google.api.services.vision.v1.model.Image;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;

import android.os.Bundle;


/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {

  // temporary flag for determining when to create/push records to the db
  int flag = 0;

  private static final Logger LOGGER = new Logger();

  // Configuration values for the prepackaged SSD model.
  private static final int TF_OD_API_INPUT_SIZE = 512;    //this is the wxh of square input size to MODEL
  private static final boolean TF_OD_API_IS_QUANTIZED = true;  //if its quantized or not. MUST be whatever the save tflite model is saved as

  private static final String TF_OD_API_MODEL_FILE = "grocery.tflite";
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/grocery_label.txt"; //LabelMap file listed classes--same order as training
  private static final DetectorMode MODE = DetectorMode.TF_OD_API;   //Using Object Detection API
  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;   //a detected prediction must have value > threshold to be displayed
  private static final boolean MAINTAIN_ASPECT = false;  //if you want to keep aspect ration or not --THIS must be same as what is expected in model,done in training
  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480); //for display ONLY specific to THIS activity
  private static final boolean SAVE_PREVIEW_BITMAP = false;  //specific to THIS activity
  private static final float TEXT_SIZE_DIP = 10;  //font size for dipsaly of bounding boxes
  OverlayView trackingOverlay;   //boudning box and prediction info is drawn on screen using an OverlayView
  private Integer sensorOrientation;  //this Activity does rotation for different Orientations

  private Classifier detector;  //class variable representing the actual model loaded up
                                // note this is  com.example.groceryhelper.localize.tflite.Classifier;

  private long lastProcessingTimeMs;   //last time processed a frame
  private Bitmap rgbFrameBitmap = null;  //various bitmap variables used in code below
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker; // this class assists with tracking bounding boxes - represents results
                                   //note this is instance of com.example.groceryhelper.localize.tracking.MultiBoxTracker;

  private BorderedText borderedText;

  TextToSpeech tts;
  @Override
  protected void onCreate(Bundle savedInstanceState){
    super.onCreate(savedInstanceState);
  tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
    @Override
    public void onInit(int status) {
      if (status == TextToSpeech.SUCCESS){
        int result = tts.setLanguage(Locale.US);
        if (result == tts.LANG_MISSING_DATA || result == tts.LANG_NOT_SUPPORTED){
          Log.e("error","Language is not supported");
        }else {
          Log.e("success","Language Supported");
        }
        Log.i("TTS","Initialize Successful");
      } else{
        Toast.makeText(getApplicationContext(),"TTS FAILED", Toast.LENGTH_LONG).show();
      }
    }
  });
  }
  /**
   * The PARENT class of this class CameraActivity is responsible for connecting to camera on Device
   * it has a callback method when the camera is ready that will call this method.
   * This method creaets the tracker (to store bounding box info), and the detector (the actual model
   * loaded from a tflite used for detection) and sets up various GUI elements and bitmaps for displaying results.
   * THis is really just a SETUP method
   **/
  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);


    //class to contain detection results with bounding box information
    tracker = new MultiBoxTracker(this);

    //specifying the size you want as input to your model...which will be used later in image processing of input images to resize them.
    int cropSize = TF_OD_API_INPUT_SIZE;

    //load up the detector based on the specified parameters include the tflite file in the assets folder, etc.
    try {
      detector =
          TFLiteObjectDetectionEfficientDet.create(
              getAssets(),
              TF_OD_API_MODEL_FILE,
              TF_OD_API_LABELS_FILE,
              TF_OD_API_INPUT_SIZE,
              TF_OD_API_IS_QUANTIZED);
      cropSize = TF_OD_API_INPUT_SIZE;
    } catch (final IOException e) {
      e.printStackTrace();
      LOGGER.e(e, "Exception initializing classifier!");
      Toast toast =
          Toast.makeText(
              getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
      toast.show();
      finish();
    }

    //display size
    previewWidth = size.getWidth();
    previewHeight = size.getHeight();



    sensorOrientation =  rotation - getScreenOrientation();   //sensorOreintation will be 0 for horizontal and 90 for portrait


    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);

    //seting up the bitmap input image  based on grabing it from the preview display of it.
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    //setting up the bitmap to store the resized input image to the size that the model expects
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

    //create a transformation that will be used to convert the input image to the right size and orientation expected by the model
    //   involves resizing (to cropsizexcropsize) from the original previewWidthxpreviewHeight
    //   involves rotation based on sensorOrientation
    //   invovles if you want aspect to be maintained
    frameToCropTransform =
        ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropSize, cropSize,
            sensorOrientation, MAINTAIN_ASPECT);  //TIP: if you want no rotation than sensorOreination should be 0

    cropToFrameTransform = new Matrix();  //identity matrix initially
    frameToCropTransform.invert(cropToFrameTransform);  //calculating the cropToFrameTransform as the inversion of the frameToCropTransform


    //grabbing a handle to the tracking_overlay which lets us draw bounding boxes inside of and this is a fragment
    // that sits on top of the ImageView where the image is displayed.
    trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(
        new DrawCallback() {
          @Override
          public void drawCallback(final Canvas canvas) {
            tracker.draw(canvas);
            if (isDebug()) {
              tracker.drawDebug(canvas);
            }
          }
        });

    //making sure the overlay fragment is same wxh and orientation as the ImageView and its image displayed inside.
    tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
  }
  // BITMAP CONVERSION WILL BE DONE IN THE TEXT_DETECTION METHOD
//  @NonNull
//  private Image getImageEncodeImage(Bitmap bitmap) {
//    Image base64EncodedImage = new Image();
//    // Convert the bitmap to a JPEG
//    // Just in case it's a format that Android understands but Cloud Vision
//    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
//    byte[] imageBytes = byteArrayOutputStream.toByteArray();
//    // Base64 encode the JPEG
//    base64EncodedImage.encodeContent(imageBytes);
//    return base64EncodedImage;
//  }

  /**
   * This method is called every time we will to process the CURRENT frame
   * this means the current frame/image will be processed by our this.detector model
   * and results are cycled through (can be more than one deteciton in an image) and displayed
   */
  @Override
  protected void processImage() {
    ++timestamp;
    final long currTimestamp = timestamp;
    trackingOverlay.postInvalidate();

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    //LOAD the current image --calling getRgbBytes method into the rgbFrameBitmap object
    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    readyForNextImage();

    //create a drawing canvas that is associated with the image croppedBitmap that will be the transformed input image to the right size and orientation
    final Canvas canvas = new Canvas(croppedBitmap);

    //CROP and transform
    //why working in portrait mode and not horizontal
    //canvas.drawBitmap(rgbFrameBitmap,new Matrix(), null);   //need to only rotate it.
   // canvas.drawBitmap(croppedBitmap, cropToFrameTransform, null); //try this later???
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);   ///crop and transform as necessary image
    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveBitmap(croppedBitmap);
    }

    //Need to run in separate thread ---to process the iamge --going to call the model to do prediction
    // because of this must run in own thread.
    runInBackground(
        new Runnable() {
          @Override
          public void run() {
            LOGGER.i("Running detection on image " + currTimestamp);
            final long startTime = SystemClock.uptimeMillis();
            final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);  //performing detection on croppedBitmap
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;


            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
            final Canvas canvas = new Canvas(cropCopyBitmap);   //create canvas to draw bounding boxes inside of which will be displayed in OverlayView
            final Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(2.0f);

            float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
            switch (MODE) {
              case TF_OD_API:
                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                break;
            }

            final List<Classifier.Recognition> mappedRecognitions =
                new LinkedList<Classifier.Recognition>();

            int saveImageOnceFlag = 1;
            String imageFileURL = "";
            //cycling through all of the recognition detections in my image I am currently processing
            for (final Classifier.Recognition result : results) {  //loop variable is result, represents one detection
              final RectF location = result.getLocation();  //getting as  a rectangle the bounding box of the result detecgiton
             final String detectClass = result.getTitle();
              if (location != null && result.getConfidence() >= minimumConfidence) { //ONLY display if the result has a confidence > threshold
                canvas.drawRect(location, paint);  //draw in the canvas the bounding boxes-->

                // Test Cropped bitmap for Google Cloud Vision
                // Cropped Bitmap for Cloud Vision
                LOGGER.d("Location for bounding box : " + location);
                Bitmap cloudCroppedBitmap = Bitmap.createBitmap(croppedBitmap,
                                                            ((int) location.left ),
                                                            ((int) location.top ),
                                                            ((int) location.width() ),
                                                          ((int) location.height()));

                LOGGER.d("Class is: " +  detectClass);
                textDetection(cloudCroppedBitmap,detectClass);
                //==============================================================
                //COVID: code to store image to CloudStore (if any results have result.getConfidence() > minimumConfidence
                //  ONLY store one time regardless of number of recognition results.
                if(saveImageOnceFlag == 1){

                  //set flag so know have already stored this image
                  saveImageOnceFlag = 0;

                  //CEMIL: code to store image (croppedBitmap) in CloudStore
                  //imageFileURL store the URL


                  //**************************************************
                  //try writing out the image being processed to a FILE
                  // File directory = Environment.getExternalStorageDirectory();
                  ContextWrapper cw = new ContextWrapper(getApplicationContext());
                  File directory = cw.getDir("imageDir", Context.MODE_PRIVATE);
                  File dest = new File(directory, "croppedImage.png");
                  File topLabelBox = new File(directory, "topLabelBoxImage.png");

                  try {
                    dest.createNewFile();
                    FileOutputStream out = new FileOutputStream(dest);
                    croppedBitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
                    out.flush();
                    out.close();
                    topLabelBox.createNewFile();
                    FileOutputStream out2 = new FileOutputStream(topLabelBox);
                    cropCopyBitmap.compress(Bitmap.CompressFormat.PNG, 90, out2);
                    out2.flush();
                    out2.close();
                  } catch (Exception e) {
                    e.printStackTrace();
                  }
                  //**************************************************
                  //

                }

                //==========================================================================
                //##################################################################


                cropToFrameTransform.mapRect(location);  //transforms using Matrix the bounding box to the correct transformed coordinates

                result.setLocation(location); // reset the newly transformed rectangle (location) representing bounding box inside the result
                mappedRecognitions.add(result);  //add the result to a linked list


              }
            }
          LOGGER.d("Did arrive here");


            tracker.trackResults(mappedRecognitions, currTimestamp);  //DOES DRAWING:  OverlayView to dispaly the recognition bounding boxes that have been transformed and stored in LL mappedRecogntions
            trackingOverlay.postInvalidate();

            computingDetection = false;
//            LOGGER.i("Calling OCR detection");
//            textDetection(croppedBitmap);

            runOnUiThread(
                new Runnable() {
                  @Override
                  public void run() {
                    showFrameInfo(previewWidth + "x" + previewHeight);
                    showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                    showInference(lastProcessingTimeMs + "ms");
                  }
                });
          }
        });
  }


  private void textDetection(final Bitmap bitmap, final String detectedClass) {
    LOGGER.i("retrieving result from google cloud: ");

    AsyncTask.execute(new Runnable() {
      @Override
      public void run() {
        try {
          // Declare Vision Builder
          Vision.Builder visionBuilder = new Vision.Builder(
                  new NetHttpTransport(),
                  new AndroidJsonFactory(),
                  null)
                  .setApplicationName("Grocery Helper")
                  ;

          visionBuilder.setVisionRequestInitializer(
                  new VisionRequestInitializer("INSERT YOUR API KEY"));

          Vision vision = visionBuilder.build();

          List<Feature> featureList = new ArrayList<>();
          Feature textDetection = new Feature();
          textDetection.setType("TEXT_DETECTION");
          textDetection.setMaxResults(3);
          featureList.add(textDetection);

          // Convert Image
          Image image = new Image();
          ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
          bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
          byte[] imageBytes = byteArrayOutputStream.toByteArray();
          image.encodeContent(imageBytes);

          List<AnnotateImageRequest> imageList = new ArrayList<>();
          AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();
          Image base64EncodedImage = image;
          annotateImageRequest.setImage(base64EncodedImage);
          annotateImageRequest.setFeatures(featureList);
          imageList.add(annotateImageRequest);
          LOGGER.i("Did coming to process image + feature");
          BatchAnnotateImagesRequest batchAnnotateImagesRequest =
                  new BatchAnnotateImagesRequest();
          batchAnnotateImagesRequest.setRequests(imageList);
          Vision.Images.Annotate annotateRequest =
                  vision.images().annotate(batchAnnotateImagesRequest);
          // Due to a bug: requests to Vision API containing large images fail when GZipped.
          annotateRequest.setDisableGZipContent(true);
          LOGGER.i("sending request to cloud API");
          BatchAnnotateImagesResponse response = annotateRequest.execute();

          StringBuilder message = new StringBuilder();
          message.append(detectedClass);
          List<EntityAnnotation> texts = response.getResponses().get(0).getTextAnnotations();
          if(texts != null){
            for (EntityAnnotation text : texts){
              message.append(String.format(Locale.US, "%s", text.getDescription()));
              tts.speak(message.toString(),TextToSpeech.QUEUE_FLUSH,null);
            }
          }else{
            message.append("nothing");
          }


          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              LOGGER.d("Result from cloud: " + message);

              Toast.makeText(getApplicationContext(),
                      message, Toast.LENGTH_LONG).show();

            }
          });

//          runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//              Toast.makeText(getApplicationContext(),
//                      text.getText(), Toast.LENGTH_LONG).show();
//            }
//          });

        } catch(GoogleJsonResponseException e){
          LOGGER.e("Request Failed" + e.getContent());
        }
        catch(IOException e) {
          LOGGER.d("IO failed", e.getMessage());
        }

      }
    });
  }




  @Override
  protected int getLayoutId() {
    return R.layout.tfe_od_camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  // Which detection model to use: by default uses Tensorflow Object Detection API frozen
  // checkpoints.
  public enum DetectorMode {
    TF_OD_API;
  }

  @Override
  protected void setUseNNAPI(final boolean isChecked) {
    runInBackground(() -> detector.setUseNNAPI(isChecked));
  }

  @Override
  protected void setNumThreads(final int numThreads) {
    runInBackground(() -> detector.setNumThreads(numThreads));
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (tts != null) {
      tts.stop();
      tts.shutdown();
    }
  }
}
