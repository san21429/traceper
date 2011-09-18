package com.traceper.android;


import java.io.ByteArrayOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import com.traceper.R;
import com.traceper.android.interfaces.IAppService;
import com.traceper.android.interfaces.IAppService.ISimpleLocationListener;
import com.traceper.android.services.AppService;

public class CameraController extends Activity implements SurfaceHolder.Callback{

	private Camera camera;
	private SurfaceView surfaceView;
	private SurfaceHolder surfaceHolder;
	private boolean isPreviewRunning;
	private ProgressDialog pdialog = null; 
	private static final int UPLOAD_PHOTO = Menu.FIRST;
	private static final int TAKE_ANOTHER_PHOTO = Menu.FIRST + 1;
	private static final int BACK = Menu.FIRST + 2;
	private static final int NOTIFICATION_ID = 0;
	private byte[] picture;
	private IAppService appService = null;
	private boolean pictureTaken = false;
	private Button takePictureButton = null;
	private boolean locationUpdated = false;
	private Location location = null;

	private Camera.PictureCallback mPictureCallbackRaw = new Camera.PictureCallback() {  
		public void onPictureTaken(byte[] data, Camera c) {  
			Log.e(getClass().getSimpleName(), "PICTURE CALLBACK RAW: " + data);  
		}  
	}; 
	private Camera.PictureCallback mPictureCallbackJpeg= new Camera.PictureCallback() { 

		public void onPictureTaken(byte[] data, Camera c) {
			picture = data;
			Log.e(getClass().getSimpleName(), "PICTURE CALLBACK JPEG: string.length = " + data.length);  
		}  
	};  

	private Camera.ShutterCallback mShutterCallback = new Camera.ShutterCallback() {  
		public void onShutter() {  
			Log.e(getClass().getSimpleName(), "SHUTTER CALLBACK");  
		}  
	};


	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {          
			appService = ((AppService.IMBinder)service).getService();    			
			locationUpdated = false;
			appService.updateLocationData(new ISimpleLocationListener() {
				@Override
				public void locationReceived(Location loc) {
					locationUpdated = true;
					location = loc;
					sendImage();
				}
			});
		}
		public void onServiceDisconnected(ComponentName className) {          
			appService = null;
		}
	};
	private NotificationManager mManager;



	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		
		this.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN );

		setContentView(R.layout.camera_view);

		getWindow().setFormat(PixelFormat.TRANSLUCENT);
		surfaceView = (SurfaceView) findViewById(R.id.surface);
		surfaceHolder = surfaceView.getHolder();
		surfaceHolder.addCallback(this);
		surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		takePictureButton = (Button) findViewById(R.id.takePictureButton);
		takePictureButton.bringToFront();
		takePictureButton.setOnClickListener(new View.OnClickListener() {			

			public void onClick(View arg0) {
				pictureTaken = true;
				camera.takePicture(mShutterCallback, mPictureCallbackRaw, mPictureCallbackJpeg);  
				openOptionsMenu();

			}
		});
		takePictureButton.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_camera,0,0,0);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		if (isPreviewRunning) {  
			camera.stopPreview();  
		}  
		//	Camera.Parameters p = camera.getParameters();  
		//	p.setPreviewSize(w, h);  

		//	camera.setParameters(p);  

		try {
			camera.setPreviewDisplay(holder);
		} catch (IOException e) {
			e.printStackTrace();
		}  

		camera.startPreview(); 
		isPreviewRunning = true; 
	}

	@Override
	public void surfaceCreated(SurfaceHolder arg0) {
		camera = Camera.open();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {
		camera.stopPreview();
		isPreviewRunning = false;
		camera.release();		
	}

	public boolean onCreateOptionsMenu(Menu menu){
		boolean result  = super.onCreateOptionsMenu(menu);
		if (pictureTaken == true) {
			menu.add(0, UPLOAD_PHOTO, 0, "Upload photo").setIcon(android.R.drawable.ic_menu_upload);
			menu.add(0, TAKE_ANOTHER_PHOTO, 1, "Take another photo").setIcon(android.R.drawable.ic_menu_camera);
		}
		menu.add(0, BACK, 2, "Back").setIcon(android.R.drawable.ic_menu_revert);

		return result;		
	}

	public boolean onPrepareOptionsMenu (Menu menu){
		boolean result = super.onPrepareOptionsMenu(menu);
		MenuItem item = menu.findItem(UPLOAD_PHOTO);
		if (item == null && pictureTaken == true) {
			menu.add(0, UPLOAD_PHOTO, 0, "Upload photo").setIcon(android.R.drawable.ic_menu_upload);
			menu.add(0, TAKE_ANOTHER_PHOTO, 1, "Take another photo").setIcon(android.R.drawable.ic_menu_camera);
		}
		else if (item != null && pictureTaken == false){
			menu.removeItem(UPLOAD_PHOTO);
			menu.removeItem(TAKE_ANOTHER_PHOTO);
		}

		return result;		
	}
	
	private void sendImage() 
	{
		mManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		
		final Notification notification = new Notification(R.drawable.icon, "Traceper", System.currentTimeMillis());

		final PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, null, 0);
		 
		if (locationUpdated == false && pictureTaken == true)
		{
			 notification.setLatestEventInfo(CameraController.this,
						 "Traceper","Waiting for location...", contentIntent);
			 mManager.notify(NOTIFICATION_ID, notification);
		}		
		else if (locationUpdated == true && pictureTaken == true)
		{
			 
			 notification.setLatestEventInfo(CameraController.this,
					 						 "Traceper","Uploading, please wait...", contentIntent);

			 mManager.notify(NOTIFICATION_ID, notification);
			

			Thread uploadThread = new Thread(){
				private Handler handler = new Handler();
				@Override
				public void run() {
					BitmapFactory.Options options = new BitmapFactory.Options();
					int inSampleSize = 10;
					int quality = 60;
					options.inSampleSize = inSampleSize;
					Bitmap bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.length, options);
					int byteCount = bitmap.getRowBytes();
					ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(byteCount);
					bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream);
				
					// 
					final int result = appService.sendImage(byteArrayOutputStream.toByteArray(), location);
					{
						handler.post(new Runnable() {							
							@Override
							public void run() {
	//							pdialog.cancel();
	//							showDialog(result);
							//	mManager.cancel(NOTIFICATION_ID);
								String str;
								if (result == IAppService.HTTP_RESPONSE_SUCCESS) {
									str = "Upload is successfull";
								}
								else {
									str = "Upload is failed. Please try again later...";
								}
								notification.setLatestEventInfo(getApplicationContext(), "Traceper", str, contentIntent);
								mManager.notify(NOTIFICATION_ID, notification);
							}
						});
					}

				};
			};
			uploadThread.start();	
		}
	}
	

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case UPLOAD_PHOTO:
/*			if (pdialog == null) {
				pdialog = new ProgressDialog(CameraController.this);
			}
			pdialog.setMessage("Processing, please wait..."); // and Uploading. Please wait...");
			pdialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			pdialog.show();
*/			sendImage();	
			CameraController.this.finish();
			break;
		case TAKE_ANOTHER_PHOTO:
			pictureTaken = false;
			camera.startPreview();  
			break;
		case BACK:
			CameraController.this.finish();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

/*
	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id){
		case IAppService.HTTP_RESPONSE_SUCCESS:
			return new AlertDialog.Builder(CameraController.this).
			setMessage("Upload is succesfull...")
			.setPositiveButton("Ok", new OnClickListener() {								
				public void onClick(DialogInterface arg0, int arg1) {
					CameraController.this.finish();
				}
			})
			.create();
		case IAppService.HTTP_RESPONSE_ERROR_UNKNOWN:
			return new AlertDialog.Builder(CameraController.this)
			.setMessage("Upload is failed. Please try again later...")
			.setPositiveButton("Ok", new OnClickListener() {								
				public void onClick(DialogInterface arg0, int arg1) {
					CameraController.this.finish();
				}
			}).create();
		}

		return super.onCreateDialog(id);
	}
*/
	public boolean onKeyDown(int keyCode, KeyEvent event)  
	{  
		if (keyCode == KeyEvent.KEYCODE_BACK) {  
			return super.onKeyDown(keyCode, event);  
		}  
		else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {  
			pictureTaken = true;
			camera.takePicture(mShutterCallback, mPictureCallbackRaw, mPictureCallbackJpeg);  
			this.openOptionsMenu();
			return true;  
		}  

		return false;  
	} 	

	protected void onResume(){
		super.onResume();
		bindService(new Intent(CameraController.this, AppService.class), mConnection , Context.BIND_AUTO_CREATE);
		//		Toast.makeText(this, "Press dpad center to take photo", Toast.LENGTH_SHORT).show();
		
	}

	@Override
	protected void onPause() {
		unbindService(mConnection);
		super.onPause();
	}
}
