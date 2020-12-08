# Grocery Helper
A prototype of Android application aimed to help vision-impaired people doing shopping in Grocery Store. When launching the app, it will tell users whether the products is a box or can and return the brand name using Text Detection from Google Cloud Vision API.  
More details can be found on [Wiki page](https://github.com/kelly-pham/GroceryHelper/wiki)
## Targets
Any Android devices that run Android 6.0 (SDK 23) and above. 
## Developing Platform and Testing Devices
* Android Studio with OpenCV version 4.0 and [Tensorflow lite](https://github.com/tensorflow/examples/tree/master/lite/examples/object_detection/android).
* Testing Device: Huawei Nexus 6P (Android 8.2)
## Proposed Design
A detailed Google doc can be found [here](https://docs.google.com/document/d/1m1FHhp76kUdA-EBSyl6WTiaeG2xf1x_imxvOML4pRik/edit).
### Proposed Flowchart
![](output\flowchart.png)
### Steps
* Data Preparation: A dataset was obtained by taking products in a real-life situation in stores like Target/Subway/Whole Foods and labeled with LabelImg. Below is some examples of the dataset, more can be found under data\images.
<table>
<tr>
    <td>Cans 1</td>
     <td>Cans 2</td>
     <td>Box 1</td>
     <td>Box 2</td>
  </tr>
  <tr>
    <td><img src="data\images%20(good%20to%20go)\IMG_0908.JPEG" width=150 height=150></td>
    <td><img src="data\images%20(good%20to%20go)\IMG_0806.JPEG" width=150 height=150></td>
    <td><img src="data\images%20(good%20to%20go)\IMG_0018.JPEG" width=150 height=150></td>
    <td><img src="data\images%20(good%20to%20go)\IMG_0094.JPEG" width=150 height=150></td>
  </tr>
 </table>

* Frame Processing: A detector will be called when a frame obtained from user's camera. The bounding box corresponding to the product types is displayed over the frame (OverlayView).
* Detector: retraining a pre-trained Efficientdet d0 with custom dataset.  
* Outputs:
<table>
<tr>
    <td>Result running on boxes</td>
     <td>Result running on cans</td>

  </tr>
  <tr>
    <td><img src="output\4.png" width=300 height=300></td>
    <td><img src="output\cans_1.PNG" width=300 height=300></td>
   
  </tr>
 </table>

### Live Demonstration
A real-life testing using the Appplication at local Target ([Youtube](https://www.youtube.com/watch?v=sLJsC1ABCeo&feature=youtu.be)).

### Future Works 
* Switching from Efficientnet to MobileNetV2 might improve the performance (speed).
* Creating a product brands database to index so that each time the app receives text from OCR components, it will make a query and only return a brand name to user (as opposed to everything for current state). 

